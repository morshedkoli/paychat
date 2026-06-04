import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:firebase_database/firebase_database.dart';
import 'package:flutter/foundation.dart';
import 'package:mime/mime.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

// ── Progress model ─────────────────────────────────────────────────────────────

class FileTransferProgress {
  /// 0.0 – 1.0
  final double progress;
  final bool isComplete;
  final bool isFailed;
  final String? errorMessage;
  final String? savedFilePath;

  const FileTransferProgress({
    this.progress = 0,
    this.isComplete = false,
    this.isFailed = false,
    this.errorMessage,
    this.savedFilePath,
  });

  static const idle = FileTransferProgress();
  static const done = FileTransferProgress(progress: 1.0, isComplete: true);
  static FileTransferProgress failed(String msg) =>
      FileTransferProgress(isFailed: true, errorMessage: msg);
  static FileTransferProgress saved(String filePath) =>
      FileTransferProgress(progress: 1.0, isComplete: true, savedFilePath: filePath);
}

// ── Service ────────────────────────────────────────────────────────────────────

/// Peer-to-peer file transfer via Firebase Realtime Database relay.
///
/// Architecture:
///   sender → chunked base64 → RTDB [file_transfers/{id}/chunks/] → receiver
///   RTDB node is deleted after transfer — the file is never permanently stored.
///
/// Chunk size: 60 KB of raw bytes → ~80 KB base64, well within RTDB node limits.
/// Max file: 50 MB (configurable via [kMaxFileSizeBytes]).
class FileTransferService {
  static const int kChunkSizeBytes = 60 * 1024; // 60 KB raw
  static const int kMaxFileSizeBytes = 50 * 1024 * 1024; // 50 MB

  final FirebaseDatabase _db;

  FileTransferService({FirebaseDatabase? db})
      : _db = db ?? FirebaseDatabase.instance;

  // ── Send ────────────────────────────────────────────────────────────────────

  /// Uploads a file to RTDB in chunks.
  /// Returns a stream of upload progress (0.0 – 1.0) then [FileTransferProgress.done].
  Stream<FileTransferProgress> sendFile({
    required String transferId,
    required String filePath,
  }) async* {
    if (kIsWeb) {
      yield FileTransferProgress.failed('File transfer not supported on web.');
      return;
    }

    final file = File(filePath);
    if (!await file.exists()) {
      yield FileTransferProgress.failed('File not found: $filePath');
      return;
    }

    final bytes = await file.readAsBytes();
    if (bytes.length > kMaxFileSizeBytes) {
      yield FileTransferProgress.failed(
          'File too large (max ${kMaxFileSizeBytes ~/ (1024 * 1024)} MB).');
      return;
    }

    final totalChunks = (bytes.length / kChunkSizeBytes).ceil();
    final ref = _db.ref('file_transfers/$transferId');

    try {
      // Write header first
      await ref.child('meta').set({
        'totalChunks': totalChunks,
        'totalBytes': bytes.length,
        'fileName': path.basename(filePath),
        'mimeType': lookupMimeType(filePath) ?? 'application/octet-stream',
        'createdAt': ServerValue.timestamp,
      });

      // Upload chunks sequentially
      for (int i = 0; i < totalChunks; i++) {
        final start = i * kChunkSizeBytes;
        final end =
            (start + kChunkSizeBytes).clamp(0, bytes.length);
        final chunk = bytes.sublist(start, end);
        final encoded = base64Encode(chunk);

        await ref.child('chunks/$i').set(encoded);

        yield FileTransferProgress(progress: (i + 1) / totalChunks);
      }

      // Signal ready
      await ref.child('meta/status').set('ready');
      yield FileTransferProgress.done;
    } catch (e) {
      yield FileTransferProgress.failed('Upload failed: $e');
      // Clean up on failure
      try {
        await ref.remove();
      } catch (_) {}
    }
  }

  // ── Receive ─────────────────────────────────────────────────────────────────

  /// Downloads a file from RTDB and saves it to the device Downloads folder.
  /// Emits progress (0.0 – 1.0), then [FileTransferProgress.saved] with the path.
  Stream<FileTransferProgress> receiveFile({
    required String transferId,
    required String fileName,
    required int totalChunks,
  }) async* {
    if (kIsWeb) {
      yield FileTransferProgress.failed('File transfer not supported on web.');
      return;
    }

    final ref = _db.ref('file_transfers/$transferId');

    // Wait for ready status (poll up to 3 minutes)
    bool isReady = false;
    for (int attempt = 0; attempt < 36; attempt++) {
      try {
        final snap = await ref.child('meta/status').get();
        if (snap.value == 'ready') {
          isReady = true;
          break;
        }
      } catch (_) {}
      await Future.delayed(const Duration(seconds: 5));
    }

    if (!isReady) {
      yield FileTransferProgress.failed('Transfer timed out — sender may have cancelled.');
      return;
    }

    // Download all chunks
    final chunkBuffers = <int, Uint8List>{};
    for (int i = 0; i < totalChunks; i++) {
      try {
        final snap = await ref.child('chunks/$i').get();
        final encoded = snap.value as String?;
        if (encoded == null) {
          yield FileTransferProgress.failed('Missing chunk $i.');
          return;
        }
        chunkBuffers[i] = base64Decode(encoded);
        yield FileTransferProgress(progress: (i + 1) / totalChunks * 0.95);
      } catch (e) {
        yield FileTransferProgress.failed('Failed to download chunk $i: $e');
        return;
      }
    }

    // Reassemble
    final allBytes = <int>[];
    for (int i = 0; i < totalChunks; i++) {
      allBytes.addAll(chunkBuffers[i]!);
    }

    // Save to device Downloads / app documents
    try {
      final dir = await _getDownloadsDir();
      // path.basename strips any directory components from a sender-controlled name.
      final sanitized = path.basename(fileName.replaceAll(RegExp(r'[^\w\s.\-]'), '_'));
      final savePath = await _uniquePath(dir, sanitized.isEmpty ? 'file' : sanitized);
      await File(savePath).writeAsBytes(Uint8List.fromList(allBytes));

      // Clean up RTDB relay — file now lives on device only
      try {
        await ref.remove();
      } catch (_) {}

      yield FileTransferProgress.saved(savePath);
    } catch (e) {
      yield FileTransferProgress.failed('Failed to save file: $e');
    }
  }

  // ── Cleanup ─────────────────────────────────────────────────────────────────

  /// Delete the RTDB relay node (called by sender after confirmed delivery,
  /// or by a TTL Cloud Function).
  Future<void> cancelTransfer(String transferId) async {
    try {
      await _db.ref('file_transfers/$transferId').remove();
    } catch (_) {}
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  Future<Directory> _getDownloadsDir() async {
    if (Platform.isAndroid) {
      // Try the public Downloads folder first
      const publicDownloads = '/storage/emulated/0/Download';
      final d = Directory(publicDownloads);
      if (await d.exists()) {
        final paychatDir = Directory('$publicDownloads/PayChat');
        await paychatDir.create(recursive: true);
        return paychatDir;
      }
    }
    // Fallback: app documents directory (always accessible)
    final appDir = await getApplicationDocumentsDirectory();
    final dl = Directory('${appDir.path}/PayChat Downloads');
    await dl.create(recursive: true);
    return dl;
  }

  Future<String> _uniquePath(Directory dir, String fileName) async {
    final candidate = '${dir.path}/$fileName';
    if (!await File(candidate).exists()) return candidate;

    final ext = path.extension(fileName);
    final base = path.basenameWithoutExtension(fileName);
    int counter = 1;
    while (true) {
      final next = '${dir.path}/$base ($counter)$ext';
      if (!await File(next).exists()) return next;
      counter++;
    }
  }

  /// Human-readable file size string
  static String formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  /// Returns an icon-appropriate category string for a MIME type
  static String mimeCategory(String? mimeType) {
    if (mimeType == null) return 'file';
    if (mimeType.startsWith('image/')) return 'image';
    if (mimeType.startsWith('video/')) return 'video';
    if (mimeType.startsWith('audio/')) return 'audio';
    if (mimeType == 'application/pdf') return 'pdf';
    if (mimeType.contains('zip') ||
        mimeType.contains('rar') ||
        mimeType.contains('tar') ||
        mimeType.contains('7z')) {
      return 'archive';
    }
    if (mimeType.contains('word') ||
        mimeType.contains('document') ||
        mimeType.endsWith('doc') ||
        mimeType.endsWith('docx')) {
      return 'doc';
    }
    if (mimeType.contains('sheet') ||
        mimeType.contains('excel') ||
        mimeType.endsWith('xls') ||
        mimeType.endsWith('xlsx')) {
      return 'sheet';
    }
    if (mimeType.contains('presentation') ||
        mimeType.endsWith('ppt') ||
        mimeType.endsWith('pptx')) {
      return 'slide';
    }
    if (mimeType.startsWith('text/')) return 'text';
    return 'file';
  }
}

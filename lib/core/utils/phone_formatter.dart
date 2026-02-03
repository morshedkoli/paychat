import 'package:flutter/services.dart';

class PhoneFormatter extends TextInputFormatter {
  static const String _bdPrefix = '+88';

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) {
    String text = newValue.text;
    
    // If the user deletes everything, let them.
    if (text.isEmpty) {
      return newValue;
    }

    // Allow '+' only at the beginning
    if (text.contains('+') && text.indexOf('+') != 0) {
      text = text.replaceFirst('+', '', 1);
    }
    
    // Remove any non-allowed characters (digits and +)
    // Actually, let's keep it simple: allow digits and +
    // logic: "automatic while typing"
    
    // Scenario 1: User types '01' -> replace with '+8801'
    if (text.startsWith('01') && !oldValue.text.startsWith(_bdPrefix)) {
      final newText = '$_bdPrefix$text';
      return TextEditingValue(
        text: newText,
        selection: TextSelection.collapsed(offset: newText.length),
      );
    }

    return newValue.copyWith(text: text);
  }

  /// Standardizes a phone number string to E.164 format for BD.
  /// If it starts with '01', adds '+88'.
  /// If it's already in '+8801...' format, returns as is.
  static String format(String number) {
    if (number.isEmpty) return number;
    String cleaned = number.replaceAll(RegExp(r'\s+'), ''); // remove spaces
    
    if (cleaned.startsWith('01')) {
      return '$_bdPrefix$cleaned';
    }
    return cleaned;
  }

  /// Validates if the number is a valid BD number (loose check).
  /// Must be +8801xxxxxxxxx (total 14 chars)
  static bool isValid(String number) {
    if (number.isEmpty) return false;
    // Regex for +8801xxxxxxxxx
    // ^\+8801[3-9]\d{8}$
    // Let's be slightly more permissive for now, just length and prefix
    final regExp = RegExp(r'^\+8801[3-9]\d{8}$');
    return regExp.hasMatch(number);
  }
}

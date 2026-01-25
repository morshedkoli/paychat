import 'package:flutter/material.dart';

class StarSparklesPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = Colors.white.withOpacity(0.3);
    // Random sparkles
    final stars = [
      const Offset(60, 120),
      const Offset(320, 180),
      const Offset(100, 500),
      const Offset(300, 600),
      const Offset(40, 700),
      // Extra for header usage
      const Offset(200, 50),
      const Offset(350, 100),
      const Offset(20, 80),
    ];

    for (var star in stars) {
      if (star.dy > size.height) continue; // Skip if outside canvas

      canvas.drawCircle(star, 2, paint);
      // Cross shape for sparkle
      canvas.drawLine(star - const Offset(4, 0), star + const Offset(4, 0),
          paint..strokeWidth = 1);
      canvas.drawLine(
          star - const Offset(0, 4), star + const Offset(0, 4), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class SmoothWavesPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withOpacity(0.03) // Very subtle
      ..style = PaintingStyle.fill;

    final path = Path();
    path.moveTo(0, size.height);
    path.lineTo(0, size.height * 0.6);
    path.quadraticBezierTo(
        size.width * 0.5, size.height * 0.4, size.width, size.height * 0.7);
    path.lineTo(size.width, size.height);
    path.close();

    canvas.drawPath(path, paint);

    final paint2 = Paint()
      ..color = Colors.white.withOpacity(0.05)
      ..style = PaintingStyle.fill;

    final path2 = Path();
    path2.moveTo(0, size.height);
    path2.lineTo(0, size.height * 0.8);
    path2.quadraticBezierTo(
        size.width * 0.25, size.height * 0.6, size.width, size.height * 0.85);
    path2.lineTo(size.width, size.height);
    path2.close();

    canvas.drawPath(path2, paint2);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// Keeping the older ones just in case
class AbstractHillsPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white.withOpacity(0.05)
      ..style = PaintingStyle.fill;

    final path = Path();
    path.moveTo(0, size.height);
    path.lineTo(0, size.height * 0.4);

    // Smooth curve
    path.quadraticBezierTo(
        size.width * 0.5, size.height * 0.2, size.width, size.height * 0.5);
    path.lineTo(size.width, size.height);
    path.close();

    canvas.drawPath(path, paint);

    // Front Hill
    final paint2 = Paint()
      ..color = Colors.white.withOpacity(0.1)
      ..style = PaintingStyle.fill;

    final path2 = Path();
    path2.moveTo(0, size.height);
    path2.lineTo(0, size.height * 0.7);
    path2.quadraticBezierTo(
        size.width * 0.3, size.height * 0.5, size.width, size.height * 0.8);
    path2.lineTo(size.width, size.height);
    path2.close();

    canvas.drawPath(path2, paint2);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

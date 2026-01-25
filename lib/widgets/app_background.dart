import 'package:flutter/material.dart';

/// Light version of app background for main screens
/// Uses subtle gradient with pattern overlays
class AppBackground extends StatelessWidget {
  final Widget child;
  final bool showSparkles;
  final bool showPattern;
  final bool useDarkGradient;

  const AppBackground({
    super.key,
    required this.child,
    this.showSparkles = false,
    this.showPattern = true,
    this.useDarkGradient = false,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // Gradient Background
        Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: useDarkGradient
                  ? const [
                      Color(0xFF2E5C9E), // Lighter Blue top
                      Color(0xFF1B3B6F), // Mid Blue
                      Color(0xFF0F2445), // Dark Blue bottom
                    ]
                  : const [
                      Color(0xFFE8EEF5), // Light blue-grey top
                      Color(0xFFF0F4F8), // Light grey mid
                      Color(0xFFF6F8FB), // Very light bottom
                    ],
              stops: const [0.0, 0.5, 1.0],
            ),
          ),
        ),

        // Pattern overlay
        if (showPattern)
          Positioned.fill(
            child: CustomPaint(
              painter: PatternPainter(isDark: useDarkGradient),
            ),
          ),

        // Subtle Waves
        Positioned.fill(
          child: CustomPaint(
            painter: LightWavesPainter(isDark: useDarkGradient),
          ),
        ),

        // Sparkles (optional)
        if (showSparkles)
          Positioned.fill(
            child: CustomPaint(
              painter: LightSparklesPainter(isDark: useDarkGradient),
            ),
          ),

        // Content
        child,
      ],
    );
  }
}

/// Geometric pattern painter - creates subtle dot/grid pattern
class PatternPainter extends CustomPainter {
  final bool isDark;

  PatternPainter({this.isDark = false});

  @override
  void paint(Canvas canvas, Size size) {
    final color = isDark
        ? Colors.white.withOpacity(0.03)
        : const Color(0xFF2E5C9E).withOpacity(0.04);

    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    // Dot pattern
    const spacing = 40.0;
    const dotRadius = 1.5;

    for (double x = 0; x < size.width; x += spacing) {
      for (double y = 0; y < size.height; y += spacing) {
        canvas.drawCircle(Offset(x, y), dotRadius, paint);
      }
    }

    // Decorative circles (large subtle shapes)
    final circlePaint = Paint()
      ..color = isDark
          ? Colors.white.withOpacity(0.02)
          : const Color(0xFF1ECAD3).withOpacity(0.03)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;

    // Top right circle
    canvas.drawCircle(
      Offset(size.width * 0.9, size.height * 0.1),
      80,
      circlePaint,
    );

    // Bottom left circle
    canvas.drawCircle(
      Offset(size.width * 0.1, size.height * 0.85),
      120,
      circlePaint,
    );

    // Center decorative circle
    canvas.drawCircle(
      Offset(size.width * 0.6, size.height * 0.5),
      60,
      circlePaint
        ..color = isDark
            ? Colors.white.withOpacity(0.015)
            : const Color(0xFF2E5C9E).withOpacity(0.02),
    );

    // Diagonal lines pattern (subtle)
    final linePaint = Paint()
      ..color = isDark
          ? Colors.white.withOpacity(0.015)
          : const Color(0xFF2E5C9E).withOpacity(0.02)
      ..strokeWidth = 0.5;

    for (double i = -size.height; i < size.width + size.height; i += 80) {
      canvas.drawLine(
        Offset(i, 0),
        Offset(i + size.height * 0.3, size.height * 0.3),
        linePaint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Light mode wave painter
class LightWavesPainter extends CustomPainter {
  final bool isDark;

  LightWavesPainter({this.isDark = false});

  @override
  void paint(Canvas canvas, Size size) {
    final color = isDark
        ? Colors.white.withOpacity(0.03)
        : const Color(0xFF2E5C9E).withOpacity(0.03);

    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    final path = Path();
    path.moveTo(0, 0);
    path.lineTo(0, size.height * 0.25);
    path.quadraticBezierTo(size.width * 0.25, size.height * 0.18,
        size.width * 0.5, size.height * 0.22);
    path.quadraticBezierTo(
        size.width * 0.75, size.height * 0.26, size.width, size.height * 0.2);
    path.lineTo(size.width, 0);
    path.close();

    canvas.drawPath(path, paint);

    final paint2 = Paint()
      ..color = isDark
          ? Colors.white.withOpacity(0.04)
          : const Color(0xFF1ECAD3).withOpacity(0.025)
      ..style = PaintingStyle.fill;

    final path2 = Path();
    path2.moveTo(0, size.height);
    path2.lineTo(0, size.height * 0.88);
    path2.quadraticBezierTo(
        size.width * 0.4, size.height * 0.82, size.width, size.height * 0.92);
    path2.lineTo(size.width, size.height);
    path2.close();

    canvas.drawPath(path2, paint2);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Light mode sparkle painter
class LightSparklesPainter extends CustomPainter {
  final bool isDark;

  LightSparklesPainter({this.isDark = false});

  @override
  void paint(Canvas canvas, Size size) {
    final color = isDark
        ? Colors.white.withOpacity(0.2)
        : const Color(0xFF2E5C9E).withOpacity(0.1);

    final paint = Paint()..color = color;

    final stars = [
      Offset(size.width * 0.1, size.height * 0.08),
      Offset(size.width * 0.88, size.height * 0.12),
      Offset(size.width * 0.12, size.height * 0.55),
      Offset(size.width * 0.92, size.height * 0.65),
      Offset(size.width * 0.05, size.height * 0.88),
      Offset(size.width * 0.78, size.height * 0.35),
      Offset(size.width * 0.45, size.height * 0.15),
      Offset(size.width * 0.55, size.height * 0.75),
    ];

    for (var star in stars) {
      // Draw 4-pointed star
      _drawStar(canvas, star, paint);
    }
  }

  void _drawStar(Canvas canvas, Offset center, Paint paint) {
    canvas.drawCircle(center, 1.5, paint);

    final linePaint = Paint()
      ..color = paint.color
      ..strokeWidth = 1.0
      ..strokeCap = StrokeCap.round;

    // Horizontal line
    canvas.drawLine(
      center - const Offset(6, 0),
      center + const Offset(6, 0),
      linePaint,
    );

    // Vertical line
    canvas.drawLine(
      center - const Offset(0, 6),
      center + const Offset(0, 6),
      linePaint,
    );

    // Diagonal lines (smaller)
    canvas.drawLine(
      center - const Offset(4, 4),
      center + const Offset(4, 4),
      linePaint..strokeWidth = 0.5,
    );
    canvas.drawLine(
      center - const Offset(4, -4),
      center + const Offset(4, -4),
      linePaint,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

import 'package:flutter/material.dart';

/// Animation duration and curve constants for consistent motion design
class AppAnimations {
  // Duration constants
  static const Duration instant = Duration(milliseconds: 0);
  static const Duration fast = Duration(milliseconds: 150);
  static const Duration normal = Duration(milliseconds: 300);
  static const Duration slow = Duration(milliseconds: 500);
  static const Duration slower = Duration(milliseconds: 700);
  static const Duration slowest = Duration(milliseconds: 1000);

  // Custom curves for different animation types
  static const Curve smoothCurve = Curves.easeInOutCubic;
  static const Curve bounceCurve = Curves.elasticOut;
  static const Curve springCurve = Curves.easeOutBack;
  static const Curve sharpCurve = Curves.easeOutQuart;
  static const Curve gentleCurve = Curves.easeInOut;

  // Page transition curves
  static const Curve pageEnterCurve = Curves.easeOutCubic;
  static const Curve pageExitCurve = Curves.easeInCubic;

  // Micro-interaction curves
  static const Curve buttonPressCurve = Curves.easeInOutQuad;
  static const Curve hoverCurve = Curves.easeOut;
  static const Curve focusCurve = Curves.easeInOut;

  // List/grid animation delays
  static Duration staggerDelay(int index, {int delayMs = 50}) {
    return Duration(milliseconds: delayMs * index);
  }

  // Common animation combinations
  static const Duration fadeInDuration = normal;
  static const Curve fadeInCurve = smoothCurve;

  static const Duration slideInDuration = normal;
  static const Curve slideInCurve = smoothCurve;

  static const Duration scaleInDuration = fast;
  static const Curve scaleInCurve = springCurve;
}

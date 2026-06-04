class AppConfig {
  /// Injected at build time via --dart-define=IMGBB_API_KEY=your_key
  /// Never hardcode the real key in source.
  static const String imgbbApiKey =
      String.fromEnvironment('IMGBB_API_KEY', defaultValue: '');

  /// Injected at build time via --dart-define=RECAPTCHA_SITE_KEY=your_key
  static const String recaptchaSiteKey =
      String.fromEnvironment('RECAPTCHA_SITE_KEY', defaultValue: '');
}

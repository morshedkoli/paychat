import 'package:paychat/models/app_user.dart';

enum SessionStatus {
  loading,
  unauthenticated,
  phoneSetupRequired,
  profileIncomplete,
  authenticated,
  blocked,
}

class SessionState {
  final SessionStatus status;
  final AppUser? user;
  final String? message;

  SessionState({
    required this.status,
    this.user,
    this.message,
  });

  factory SessionState.loading() => SessionState(status: SessionStatus.loading);

  factory SessionState.unauthenticated({String? message}) => SessionState(
    status: SessionStatus.unauthenticated,
    message: message,
  );

  factory SessionState.phoneSetupRequired(AppUser user) => SessionState(
    status: SessionStatus.phoneSetupRequired,
    user: user,
  );

  factory SessionState.profileIncomplete(AppUser user) => SessionState(
    status: SessionStatus.profileIncomplete,
    user: user,
  );

  factory SessionState.authenticated(AppUser user) => SessionState(
    status: SessionStatus.authenticated,
    user: user,
  );

  factory SessionState.blocked(AppUser user) => SessionState(
    status: SessionStatus.blocked,
    user: user,
  );

  SessionState copyWith({
    SessionStatus? status,
    AppUser? user,
    String? message,
  }) {
    return SessionState(
      status: status ?? this.status,
      user: user ?? this.user,
      message: message ?? this.message,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is SessionState &&
          runtimeType == other.runtimeType &&
          status == other.status &&
          user == other.user &&
          message == other.message;

  @override
  int get hashCode => status.hashCode ^ user.hashCode ^ message.hashCode;
}

enum AuthEntryStep {
  phone,
  otp,
}

class AuthEntryState {
  final AuthEntryStep step;
  final String countryIso;
  final String countryDialCode;
  final String nationalNumber;
  final String phoneNumberE164;
  final String? otpSentTo;
  final String? verificationId;
  final bool isSendingCode;
  final bool isVerifyingCode;
  final String? errorMessage;

  AuthEntryState({
    this.step = AuthEntryStep.phone,
    this.countryIso = 'BD',
    this.countryDialCode = '+880',
    this.nationalNumber = '',
    this.phoneNumberE164 = '',
    this.otpSentTo,
    this.verificationId,
    this.isSendingCode = false,
    this.isVerifyingCode = false,
    this.errorMessage,
  });

  AuthEntryState copyWith({
    AuthEntryStep? step,
    String? countryIso,
    String? countryDialCode,
    String? nationalNumber,
    String? phoneNumberE164,
    String? otpSentTo,
    String? verificationId,
    bool? isSendingCode,
    bool? isVerifyingCode,
    String? errorMessage,
  }) {
    return AuthEntryState(
      step: step ?? this.step,
      countryIso: countryIso ?? this.countryIso,
      countryDialCode: countryDialCode ?? this.countryDialCode,
      nationalNumber: nationalNumber ?? this.nationalNumber,
      phoneNumberE164: phoneNumberE164 ?? this.phoneNumberE164,
      otpSentTo: otpSentTo ?? this.otpSentTo,
      verificationId: verificationId ?? this.verificationId,
      isSendingCode: isSendingCode ?? this.isSendingCode,
      isVerifyingCode: isVerifyingCode ?? this.isVerifyingCode,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AuthEntryState &&
          runtimeType == other.runtimeType &&
          step == other.step &&
          countryIso == other.countryIso &&
          countryDialCode == other.countryDialCode &&
          nationalNumber == other.nationalNumber &&
          phoneNumberE164 == other.phoneNumberE164 &&
          otpSentTo == other.otpSentTo &&
          verificationId == other.verificationId &&
          isSendingCode == other.isSendingCode &&
          isVerifyingCode == other.isVerifyingCode &&
          errorMessage == other.errorMessage;

  @override
  int get hashCode =>
      step.hashCode ^
      countryIso.hashCode ^
      countryDialCode.hashCode ^
      nationalNumber.hashCode ^
      phoneNumberE164.hashCode ^
      otpSentTo.hashCode ^
      verificationId.hashCode ^
      isSendingCode.hashCode ^
      isVerifyingCode.hashCode ^
      errorMessage.hashCode;
}

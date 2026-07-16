import 'package:flutter/foundation.dart';

enum ScreenRoute { events, queue, booking, myHistory }

class Session with ChangeNotifier {
  static final Session _instance = Session._internal();

  factory Session() {
    return _instance;
  }

  Session._internal();

  String? _usrId;
  String? _usrNm;
  bool _isLoggedIn = false;
  ScreenRoute _currentScreen = ScreenRoute.events;

  // 대기열 상태
  String? _schdNo;
  String? _entryToken;
  int? _queuePosition;
  int? _queueTotalWaiting;
  int? _etaSec;
  int? _ttlSec; // 입장 유효시간

  // 예매 선택 상태
  List<String> _selectedSeats = [];
  int _holdTtlSec = 0;

  // Getters
  String? get usrId => _usrId;
  String? get usrNm => _usrNm;
  bool get isLoggedIn => _isLoggedIn;
  ScreenRoute get currentScreen => _currentScreen;

  String? get schdNo => _schdNo;
  String? get entryToken => _entryToken;
  int? get queuePosition => _queuePosition;
  int? get queueTotalWaiting => _queueTotalWaiting;
  int? get etaSec => _etaSec;
  int? get ttlSec => _ttlSec;

  List<String> get selectedSeats => _selectedSeats;
  int get holdTtlSec => _holdTtlSec;

  // Setters
  void setLogin(String usrId, String usrNm) {
    _usrId = usrId;
    _usrNm = usrNm;
    _isLoggedIn = true;
    notifyListeners();
  }

  void logout() {
    _usrId = null;
    _usrNm = null;
    _isLoggedIn = false;
    _currentScreen = ScreenRoute.events;
    _schdNo = null;
    _entryToken = null;
    _selectedSeats.clear();
    notifyListeners();
  }

  void setScreen(ScreenRoute route) {
    _currentScreen = route;
    notifyListeners();
  }

  void enterQueue(String schdNo) {
    _schdNo = schdNo;
    _entryToken = null;
    _queuePosition = null;
    _currentScreen = ScreenRoute.queue;
    notifyListeners();
  }

  void updateQueueStatus({
    required bool admitted,
    required bool inQueue,
    String? entryToken,
    int? ttlSec,
    int? position,
    int? totalWaiting,
    int? etaSec,
  }) {
    if (admitted && entryToken != null) {
      _entryToken = entryToken;
      _ttlSec = ttlSec;
    }
    _queuePosition = position;
    _queueTotalWaiting = totalWaiting;
    _etaSec = etaSec;
    notifyListeners();
  }

  void selectSeats(List<String> seats) {
    _selectedSeats = seats;
    notifyListeners();
  }

  void setHoldTtlSec(int sec) {
    _holdTtlSec = sec;
    notifyListeners();
  }

  void moveToBooking() {
    _currentScreen = ScreenRoute.booking;
    notifyListeners();
  }

  void clearBookingState() {
    _selectedSeats.clear();
    _holdTtlSec = 0;
    _entryToken = null;
    _ttlSec = null;
    _schdNo = null;
  }
}

import 'package:flutter/material.dart';
import 'session.dart';
import 'screens/login_screen.dart';
import 'screens/events_screen.dart';
import 'screens/queue_wait_screen.dart';
import 'screens/booking_screen.dart';
import 'screens/my_history_screen.dart';

void main() {
  runApp(const TicketingFlowApp());
}

class TicketingFlowApp extends StatelessWidget {
  const TicketingFlowApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'TicketingFlow',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF4B5BA8),
          brightness: Brightness.light,
        ),
        // 하단 액션 버튼을 가리지 않도록 알림을 띄워서 위로 올린다
        snackBarTheme: const SnackBarThemeData(
          behavior: SnackBarBehavior.floating,
          insetPadding: EdgeInsets.fromLTRB(16, 0, 16, 88),
        ),
      ),
      home: const _RootNavigation(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class _RootNavigation extends StatefulWidget {
  const _RootNavigation({Key? key}) : super(key: key);

  @override
  State<_RootNavigation> createState() => _RootNavigationState();
}

class _RootNavigationState extends State<_RootNavigation> {
  late Session _session;

  @override
  void initState() {
    super.initState();
    _session = Session();
    _session.addListener(() {
      setState(() {});
    });
  }

  @override
  void dispose() {
    _session.removeListener(() {});
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_session.isLoggedIn) {
      return LoginScreen(session: _session);
    }

    if (_session.currentScreen == ScreenRoute.queue) {
      return QueueWaitScreen(session: _session);
    }

    if (_session.currentScreen == ScreenRoute.booking) {
      return BookingScreen(session: _session);
    }

    if (_session.currentScreen == ScreenRoute.myHistory) {
      return MyHistoryScreen(session: _session);
    }

    return EventsScreen(session: _session);
  }
}

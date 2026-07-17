import 'package:flutter/material.dart';
import 'dart:async';
import '../session.dart';
import '../api_client.dart';

class QueueWaitScreen extends StatefulWidget {
  final Session session;

  const QueueWaitScreen({Key? key, required this.session}) : super(key: key);

  @override
  State<QueueWaitScreen> createState() => _QueueWaitScreenState();
}

class _QueueWaitScreenState extends State<QueueWaitScreen> with TickerProviderStateMixin {
  final ApiClient _apiClient = ApiClient();
  late AnimationController _animController;
  Timer? _pollTimer;
  bool _isAdmitted = false;
  bool _isInQueue = false;

  @override
  void initState() {
    super.initState();
    _apiClient.init();
    _animController = AnimationController(
      duration: const Duration(seconds: 2),
      vsync: this,
    )..repeat();

    _enterQueue();
  }

  Future<void> _enterQueue() async {
    if (widget.session.schdNo == null || widget.session.usrId == null) {
      _showError('스케줄 정보가 없습니다.');
      return;
    }

    try {
      final response = await _apiClient.queueEnter(
        widget.session.schdNo!,
        widget.session.usrId!,
      );

      if (response.isSuccess && response.data != null) {
        final data = response.data!;
        widget.session.updateQueueStatus(
          admitted: data['admitted'] as bool? ?? false,
          inQueue: data['inQueue'] as bool? ?? false,
          entryToken: data['entryToken'] as String?,
          ttlSec: data['ttlSec'] as int?,
          position: data['position'] as int?,
          totalWaiting: data['totalWaiting'] as int?,
          etaSec: data['etaSec'] as int?,
        );

        if (!mounted) return;
        setState(() {
          _isAdmitted = data['admitted'] as bool? ?? false;
          _isInQueue = data['inQueue'] as bool? ?? false;
        });

        if (_isAdmitted) {
          _onAdmitted();
        } else if (_isInQueue) {
          _startPolling(data['pollAfterMs'] as int? ?? 2000);
        }
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('대기열 진입 실패: $e');
    }
  }

  void _startPolling(int intervalMs) {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(Duration(milliseconds: intervalMs), (_) async {
      if (widget.session.schdNo == null || widget.session.usrId == null) return;

      try {
        final response = await _apiClient.queueStatus(
          widget.session.schdNo!,
          widget.session.usrId!,
        );

        if (response.isSuccess && response.data != null) {
          final data = response.data!;
          final admitted = data['admitted'] as bool? ?? false;
          final inQueue = data['inQueue'] as bool? ?? false;

          if (!mounted) return;
          setState(() {
            _isAdmitted = admitted;
            _isInQueue = inQueue;
          });

          widget.session.updateQueueStatus(
            admitted: admitted,
            inQueue: inQueue,
            entryToken: data['entryToken'] as String?,
            ttlSec: data['ttlSec'] as int?,
            position: data['position'] as int?,
            totalWaiting: data['totalWaiting'] as int?,
            etaSec: data['etaSec'] as int?,
          );

          if (admitted) {
            _pollTimer?.cancel();
            _onAdmitted();
          } else {
            _startPolling(data['pollAfterMs'] as int? ?? 2000);
          }
        }
      } catch (e) {
        // 폴링 오류는 무시하고 계속 시도
      }
    });
  }

  void _onAdmitted() {
    _pollTimer?.cancel();
    if (mounted) {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => AlertDialog(
          title: const Text('입장 승인됨'),
          content: const Text('예매 페이지로 이동합니다.'),
          actions: [
            ElevatedButton(
              onPressed: () {
                Navigator.of(context).pop();
                widget.session.moveToBooking();
              },
              child: const Text('확인'),
            ),
          ],
        ),
      );
    }
  }

  /// 데모: 내 뒤로 가상 대기자를 추가해 실시간 유입(전체 대기 증가)을 보여준다.
  /// 신규 봇의 score는 현재 시각이라 내 순번에는 영향이 없다.
  Future<void> _handleAddCrowd() async {
    if (widget.session.schdNo == null) return;
    try {
      final response = await _apiClient.demoLoad(widget.session.schdNo!, 100);
      if (!mounted) return;
      if (response.isSuccess && response.data != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('대기자 100명이 뒤에 합류했습니다 (전체 ${response.data!['totalWaiting']}명)'),
            backgroundColor: Colors.green[700],
          ),
        );
      }
    } catch (_) {
      // 데모 부가 기능 — 실패해도 대기 흐름은 유지
    }
  }

  Future<void> _handleLeaveQueue() async {
    if (widget.session.schdNo == null || widget.session.usrId == null) return;

    try {
      await _apiClient.queueLeave(
        widget.session.schdNo!,
        widget.session.usrId!,
      );
      if (mounted) {
        widget.session.setScreen(ScreenRoute.events);
      }
    } catch (e) {
      _showError('대기열 나가기 실패: $e');
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: Colors.red[700]),
    );
  }

  String _formatTime(int? seconds) {
    if (seconds == null) return '계산 중...';
    final mins = seconds ~/ 60;
    final secs = seconds % 60;
    return '약 ${mins}분 ${secs}초';
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () async {
        await _handleLeaveQueue();
        return false;
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('대기열'),
          elevation: 0,
          leading: BackButton(onPressed: _handleLeaveQueue),
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // 애니메이션 진행 바
                ScaleTransition(
                  scale: Tween(begin: 0.8, end: 1.2).animate(
                    CurvedAnimation(parent: _animController, curve: Curves.easeInOut),
                  ),
                  child: Container(
                    width: 100,
                    height: 100,
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.primary.withOpacity(0.1),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.hourglass_empty,
                      size: 50,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
                ),
                const SizedBox(height: 32),
                Text(
                  '대기 중입니다',
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
                const SizedBox(height: 24),
                // 대기 순번
                if (widget.session.queuePosition != null)
                  Column(
                    children: [
                      Text(
                        '현재 순번',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Colors.grey[600],
                            ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '${widget.session.queuePosition}',
                        style: Theme.of(context).textTheme.displayMedium?.copyWith(
                              color: Theme.of(context).colorScheme.primary,
                              fontWeight: FontWeight.bold,
                            ),
                      ),
                      const SizedBox(height: 16),
                    ],
                  ),
                // 전체 대기 수
                if (widget.session.queueTotalWaiting != null)
                  Text(
                    '전체 대기: ${widget.session.queueTotalWaiting}명',
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                const SizedBox(height: 24),
                // 예상 시간
                if (widget.session.etaSec != null)
                  Column(
                    children: [
                      Text(
                        '예상 입장 시간',
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: Colors.grey[600],
                            ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        _formatTime(widget.session.etaSec),
                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                              color: Theme.of(context).colorScheme.primary,
                            ),
                      ),
                    ],
                  ),
                const SizedBox(height: 48),
                // 데모: 실시간 유입 시뮬레이션 — 내 뒤로 가상 대기자 추가
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: FilledButton.tonalIcon(
                    onPressed: _handleAddCrowd,
                    icon: const Icon(Icons.groups),
                    label: const Text('실시간 유입 시뮬레이션 — 내 뒤로 +100명'),
                  ),
                ),
                const SizedBox(height: 12),
                // 나가기 버튼
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: OutlinedButton(
                    onPressed: _handleLeaveQueue,
                    child: const Text('대기열에서 나가기'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _animController.dispose();
    super.dispose();
  }
}

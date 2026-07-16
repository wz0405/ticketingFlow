import 'package:flutter/material.dart';
import '../session.dart';
import '../api_client.dart';

class MyHistoryScreen extends StatefulWidget {
  final Session session;

  const MyHistoryScreen({Key? key, required this.session}) : super(key: key);

  @override
  State<MyHistoryScreen> createState() => _MyHistoryScreenState();
}

class _MyHistoryScreenState extends State<MyHistoryScreen> with SingleTickerProviderStateMixin {
  final ApiClient _apiClient = ApiClient();
  late TabController _tabController;
  List<Map<String, dynamic>> _reservations = [];
  List<Map<String, dynamic>> _orders = [];
  bool _isLoadingRsv = false;
  bool _isLoadingOrd = false;

  @override
  void initState() {
    super.initState();
    _apiClient.init();
    _tabController = TabController(length: 2, vsync: this);
    _loadReservations();
    _loadOrders();
  }

  Future<void> _loadReservations() async {
    if (widget.session.usrId == null) return;

    setState(() => _isLoadingRsv = true);
    try {
      final response = await _apiClient.myRsv(widget.session.usrId!);

      if (response.isSuccess && response.data != null) {
        final dataMap = response.data!;
        final list = dataMap['list'] as List? ?? [];
        setState(() {
          _reservations = list.cast<Map<String, dynamic>>();
        });
      }
    } catch (e) {
      _showError('예매 내역 로드 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoadingRsv = false);
    }
  }

  Future<void> _loadOrders() async {
    if (widget.session.usrId == null) return;

    setState(() => _isLoadingOrd = true);
    try {
      final response = await _apiClient.myOrd(widget.session.usrId!);

      if (response.isSuccess && response.data != null) {
        final dataMap = response.data!;
        final list = dataMap['list'] as List? ?? [];
        setState(() {
          _orders = list.cast<Map<String, dynamic>>();
        });
      }
    } catch (e) {
      _showError('주문 내역 로드 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoadingOrd = false);
    }
  }

  Future<void> _cancelReservation(String rsvNo) async {
    if (widget.session.usrId == null) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('예매 취소'),
        content: const Text('예매를 취소하시겠습니까?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('아니오'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('예'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      final response = await _apiClient.rsvCancel(rsvNo, widget.session.usrId!);

      if (!mounted) return;

      if (response.isSuccess) {
        _showInfo('예매가 취소되었습니다.');
        _loadReservations();
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('취소 실패: $e');
    }
  }

  Future<void> _cancelOrder(String ordNo) async {
    if (widget.session.usrId == null) return;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('주문 취소'),
        content: const Text('주문을 취소하시겠습니까?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('아니오'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('예'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      final response = await _apiClient.ordCancel(ordNo, widget.session.usrId!);

      if (!mounted) return;

      if (response.isSuccess) {
        _showInfo('주문이 취소되었습니다.');
        _loadOrders();
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('취소 실패: $e');
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: Colors.red[700]),
    );
  }

  void _showInfo(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: Colors.green[700]),
    );
  }

  String _formatAmount(int amt) {
    return '${(amt).toString().replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]},')}원';
  }

  String _formatDate(String? dateStr) {
    if (dateStr == null || dateStr.isEmpty) return '';
    if (dateStr.length >= 8) {
      final year = dateStr.substring(0, 4);
      final month = dateStr.substring(4, 6);
      final day = dateStr.substring(6, 8);
      return '$year.$month.$day';
    }
    return dateStr;
  }

  String _getRsvStatusLabel(String stCd) {
    switch (stCd) {
      case '0':
        return '확정';
      case '2':
        return '취소';
      default:
        return stCd;
    }
  }

  Color _getRsvStatusColor(String stCd) {
    switch (stCd) {
      case '0':
        return Colors.green;
      case '2':
        return Colors.grey;
      default:
        return Colors.blue;
    }
  }

  void _goBackToEvents() {
    widget.session.setScreen(ScreenRoute.events);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('내 내역'),
        elevation: 0,
        leading: BackButton(onPressed: _goBackToEvents),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: '예매'),
            Tab(text: '주문'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildReservationTab(),
          _buildOrderTab(),
        ],
      ),
    );
  }

  Widget _buildReservationTab() {
    if (_isLoadingRsv) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_reservations.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.confirmation_number_outlined, size: 64, color: Colors.grey[400]),
            const SizedBox(height: 16),
            Text(
              '예매 내역이 없습니다.',
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    color: Colors.grey[600],
                  ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _goBackToEvents,
              child: const Text('이벤트 목록으로'),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadReservations,
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: _reservations.length,
        itemBuilder: (context, index) {
          final rsv = _reservations[index];
          final rsvNo = rsv['rsvNo'] as String? ?? '';
          final evntNm = rsv['evntNm'] as String? ?? '';
          final schdDt = _formatDate(rsv['schdDt'] as String?);
          final seatCnt = rsv['seatCnt'] as int? ?? 0;
          final totAmt = rsv['totAmt'] as int? ?? 0;
          final rsvStCd = rsv['rsvStCd'] as String? ?? '0';
          final seatNos = rsv['seatNos'] as String? ?? '';

          final isConfirmed = rsvStCd == '0';

          return Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              evntNm,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 16,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '예매번호: $rsvNo',
                              style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: _getRsvStatusColor(rsvStCd).withOpacity(0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          _getRsvStatusLabel(rsvStCd),
                          style: TextStyle(
                            color: _getRsvStatusColor(rsvStCd),
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '공연일',
                              style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                            ),
                            Text(schdDt, style: const TextStyle(fontWeight: FontWeight.w500)),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '좌석 수',
                              style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                            ),
                            Text('$seatCnt석', style: const TextStyle(fontWeight: FontWeight.w500)),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text(
                              '금액',
                              style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                            ),
                            Text(
                              _formatAmount(totAmt),
                              style: const TextStyle(fontWeight: FontWeight.bold),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  if (seatNos.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(
                      '좌석: $seatNos',
                      style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                    ),
                  ],
                  if (isConfirmed) ...[
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      height: 32,
                      child: OutlinedButton(
                        onPressed: () => _cancelReservation(rsvNo),
                        child: const Text('취소'),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildOrderTab() {
    if (_isLoadingOrd) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_orders.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.shopping_bag_outlined, size: 64, color: Colors.grey[400]),
            const SizedBox(height: 16),
            Text(
              '주문 내역이 없습니다.',
              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                    color: Colors.grey[600],
                  ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _goBackToEvents,
              child: const Text('이벤트 목록으로'),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _loadOrders,
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: _orders.length,
        itemBuilder: (context, index) {
          final ord = _orders[index];
          final ordNo = ord['ordNo'] as String? ?? '';
          final evntNm = ord['evntNm'] as String? ?? '';
          final itemCnt = ord['itemCnt'] as int? ?? 0;
          final totAmt = ord['totAmt'] as int? ?? 0;
          final ordStCd = ord['ordStCd'] as String? ?? '0';
          final itemDesc = ord['itemDesc'] as String? ?? '';

          final isConfirmed = ordStCd == '0';

          return Card(
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              evntNm,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 16,
                              ),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '주문번호: $ordNo',
                              style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                            ),
                          ],
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: isConfirmed ? Colors.green.withOpacity(0.2) : Colors.grey.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          isConfirmed ? '확정' : '취소',
                          style: TextStyle(
                            color: isConfirmed ? Colors.green : Colors.grey,
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    '상품',
                    style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                  ),
                  Text(
                    itemDesc,
                    style: const TextStyle(fontWeight: FontWeight.w500),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 8),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '상품 수: $itemCnt개',
                        style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                      ),
                      Text(
                        _formatAmount(totAmt),
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  if (isConfirmed) ...[
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      height: 32,
                      child: OutlinedButton(
                        onPressed: () => _cancelOrder(ordNo),
                        child: const Text('취소'),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }
}

import 'package:flutter/material.dart';
import 'dart:async';
import '../session.dart';
import '../api_client.dart';

class BookingScreen extends StatefulWidget {
  final Session session;

  const BookingScreen({Key? key, required this.session}) : super(key: key);

  @override
  State<BookingScreen> createState() => _BookingScreenState();
}

class _BookingScreenState extends State<BookingScreen> with TickerProviderStateMixin {
  final ApiClient _apiClient = ApiClient();
  late TabController _tabController;
  Timer? _ttlTimer;
  int _remainTtlSec = 0;

  List<Map<String, dynamic>> _seats = [];
  List<Map<String, dynamic>> _products = [];
  Map<String, int> _selectedProducts = {}; // {prdNo: qty}
  bool _isLoadingSeat = false;
  bool _isLoadingPrd = false;
  bool _isHoldingSeats = false;

  @override
  void initState() {
    super.initState();
    _apiClient.init();
    _tabController = TabController(length: 2, vsync: this);
    _remainTtlSec = widget.session.ttlSec ?? 300;
    _startTtlTimer();
    _loadSeats();
    _loadProducts();
  }

  void _startTtlTimer() {
    _ttlTimer?.cancel();
    _ttlTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() {
        _remainTtlSec--;
        if (_remainTtlSec <= 0) {
          _onEntryExpired();
        }
      });
    });
  }

  void _onEntryExpired() {
    _ttlTimer?.cancel();
    if (mounted) {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => AlertDialog(
          title: const Text('입장 시간 만료'),
          content: const Text('입장 유효시간이 종료되었습니다. 대기열로 돌아갑니다.'),
          actions: [
            ElevatedButton(
              onPressed: () {
                Navigator.of(context).pop();
                widget.session.clearBookingState();
                widget.session.setScreen(ScreenRoute.queue);
              },
              child: const Text('확인'),
            ),
          ],
        ),
      );
    }
  }

  Future<void> _loadSeats() async {
    if (widget.session.schdNo == null || widget.session.usrId == null) return;

    setState(() => _isLoadingSeat = true);
    try {
      final response = await _apiClient.seatList(
        widget.session.schdNo!,
        widget.session.usrId!,
      );

      if (response.isSuccess && response.data != null) {
        final dataMap = response.data!;
        final list = dataMap['list'] as List? ?? [];
        setState(() {
          _seats = list.cast<Map<String, dynamic>>();
        });
      }
    } catch (e) {
      _showError('좌석 목록 로드 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoadingSeat = false);
    }
  }

  Future<void> _loadProducts() async {
    if (widget.session.schdNo == null) return;

    setState(() => _isLoadingPrd = true);
    try {
      final response = await _apiClient.prdList(widget.session.schdNo!);

      if (response.isSuccess && response.data != null) {
        final dataMap = response.data!;
        final list = dataMap['list'] as List? ?? [];
        setState(() {
          _products = list.cast<Map<String, dynamic>>();
        });
      }
    } catch (e) {
      _showError('상품 목록 로드 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoadingPrd = false);
    }
  }

  void _toggleSeatSelection(String seatNo, String stCd) {
    if (stCd == 'S') {
      _showError('이미 판매 완료된 좌석입니다.');
      return;
    }
    if (stCd == 'H') {
      _showError('이미 선점된 좌석입니다.');
      return;
    }

    setState(() {
      if (widget.session.selectedSeats.contains(seatNo)) {
        widget.session.selectedSeats.remove(seatNo);
      } else {
        if (widget.session.selectedSeats.length >= 4) {
          _showError('최대 4석까지만 선택 가능합니다.');
          return;
        }
        widget.session.selectedSeats.add(seatNo);
      }
    });
  }

  Future<void> _holdSeats() async {
    if (widget.session.selectedSeats.isEmpty) {
      _showError('좌석을 선택해주세요.');
      return;
    }

    if (widget.session.schdNo == null || widget.session.usrId == null || widget.session.entryToken == null) {
      _showError('필수 정보가 없습니다.');
      return;
    }

    setState(() => _isHoldingSeats = true);
    try {
      final response = await _apiClient.rsvHold(
        widget.session.schdNo!,
        widget.session.usrId!,
        widget.session.entryToken!,
        widget.session.selectedSeats,
      );

      if (!mounted) return;

      if (response.isSuccess && response.data != null) {
        final holdTtlSec = response.data!['holdTtlSec'] as int? ?? 600;
        widget.session.setHoldTtlSec(holdTtlSec);
        _showInfo('좌석 선점 완료. ${holdTtlSec}초 안에 예매를 확정해주세요.');
      } else if (response.rsltCd == '3001') {
        _showError('이미 선점된 좌석이 있습니다. 좌석을 다시 선택해주세요.');
        await Future.delayed(const Duration(milliseconds: 500));
        _loadSeats();
      } else if (response.rsltCd == '2002') {
        _showError('입장 시간이 만료되었습니다.');
        widget.session.clearBookingState();
        widget.session.setScreen(ScreenRoute.queue);
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('좌석 선점 실패: $e');
    } finally {
      if (mounted) setState(() => _isHoldingSeats = false);
    }
  }

  Future<void> _confirmReservation() async {
    if (widget.session.selectedSeats.isEmpty) {
      _showError('좌석을 선택해주세요.');
      return;
    }

    if (widget.session.schdNo == null || widget.session.usrId == null || widget.session.entryToken == null) {
      _showError('필수 정보가 없습니다.');
      return;
    }

    try {
      final response = await _apiClient.rsvConfirm(
        widget.session.schdNo!,
        widget.session.usrId!,
        widget.session.entryToken!,
        widget.session.selectedSeats,
      );

      if (!mounted) return;

      if (response.isSuccess && response.data != null) {
        final rsvNo = response.data!['rsvNo'] as String? ?? '';
        final totAmt = response.data!['totAmt'] as int? ?? 0;
        final seatCnt = response.data!['seatCnt'] as int? ?? 0;

        showDialog(
          context: context,
          barrierDismissible: false,
          builder: (context) => AlertDialog(
            title: const Text('예매 완료'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _dialogRow('예매번호', rsvNo),
                const SizedBox(height: 8),
                _dialogRow('좌석 수', '$seatCnt석'),
                const SizedBox(height: 8),
                _dialogRow('총 금액', _formatAmount(totAmt)),
              ],
            ),
            actions: [
              ElevatedButton(
                onPressed: () {
                  Navigator.of(context).pop();
                  widget.session.clearBookingState();
                  widget.session.setScreen(ScreenRoute.myHistory);
                },
                child: const Text('내 예매 내역 보기'),
              ),
            ],
          ),
        );
      } else if (response.rsltCd == '3001') {
        _showError('좌석이 이미 판매되었습니다.');
        _loadSeats();
      } else if (response.rsltCd == '2002') {
        _showError('입장 시간이 만료되었습니다.');
        widget.session.clearBookingState();
        widget.session.setScreen(ScreenRoute.queue);
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('예매 확정 실패: $e');
    }
  }

  Future<void> _confirmOrder() async {
    if (_selectedProducts.isEmpty) {
      _showError('상품을 선택해주세요.');
      return;
    }

    if (widget.session.schdNo == null || widget.session.usrId == null || widget.session.entryToken == null) {
      _showError('필수 정보가 없습니다.');
      return;
    }

    final items = _selectedProducts.entries.map((e) => {
      'prdNo': e.key,
      'qty': e.value,
    }).toList();

    try {
      final response = await _apiClient.ordConfirm(
        widget.session.schdNo!,
        widget.session.usrId!,
        widget.session.entryToken!,
        items,
      );

      if (!mounted) return;

      if (response.isSuccess && response.data != null) {
        final ordNo = response.data!['ordNo'] as String? ?? '';
        final totAmt = response.data!['totAmt'] as int? ?? 0;
        final itemCnt = response.data!['itemCnt'] as int? ?? 0;

        showDialog(
          context: context,
          barrierDismissible: false,
          builder: (context) => AlertDialog(
            title: const Text('주문 완료'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _dialogRow('주문번호', ordNo),
                const SizedBox(height: 8),
                _dialogRow('상품 수', '$itemCnt개'),
                const SizedBox(height: 8),
                _dialogRow('총 금액', _formatAmount(totAmt)),
              ],
            ),
            actions: [
              ElevatedButton(
                onPressed: () {
                  Navigator.of(context).pop();
                  widget.session.clearBookingState();
                  widget.session.setScreen(ScreenRoute.myHistory);
                },
                child: const Text('내 주문 내역 보기'),
              ),
            ],
          ),
        );
      } else if (response.rsltCd == '2002') {
        _showError('입장 시간이 만료되었습니다.');
        widget.session.clearBookingState();
        widget.session.setScreen(ScreenRoute.queue);
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('주문 실패: $e');
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

  Widget _dialogRow(String label, String value) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: const TextStyle(fontWeight: FontWeight.w500)),
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('예매'),
        elevation: 0,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(60),
          child: Column(
            children: [
              TabBar(
                controller: _tabController,
                tabs: const [
                  Tab(text: '좌석'),
                  Tab(text: '상품'),
                ],
              ),
              Container(
                color: Colors.red[100],
                padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.timer, size: 16, color: Colors.red[700]),
                    const SizedBox(width: 8),
                    Text(
                      '입장 시간: $_remainTtlSec초 남음',
                      style: TextStyle(
                        color: Colors.red[700],
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // 좌석 탭
          _buildSeatTab(),
          // 상품 탭
          _buildProductTab(),
        ],
      ),
    );
  }

  Widget _buildSeatTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Expanded(
            child: _isLoadingSeat
                ? const Center(child: CircularProgressIndicator())
                : SingleChildScrollView(
                    child: _buildSeatGrid(),
                  ),
          ),
          const SizedBox(height: 16),
          // 선택된 좌석 표시
          if (widget.session.selectedSeats.isNotEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.blue[50],
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '선택한 좌석: ${widget.session.selectedSeats.join(', ')}',
                    style: const TextStyle(fontWeight: FontWeight.w500),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 12),
          // 버튼
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: widget.session.selectedSeats.isEmpty || _isHoldingSeats
                      ? null
                      : _holdSeats,
                  child: _isHoldingSeats
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('좌석 선점'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton(
                  onPressed: widget.session.selectedSeats.isEmpty || widget.session.holdTtlSec == 0
                      ? null
                      : _confirmReservation,
                  child: const Text('예매 확정'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSeatGrid() {
    // 좌석을 행별로 그룹화
    final Map<String, List<Map<String, dynamic>>> seatsByRow = {};
    for (final seat in _seats) {
      final seatNo = seat['seatNo'] as String? ?? '';
      final row = seatNo.split('-').first;
      if (!seatsByRow.containsKey(row)) {
        seatsByRow[row] = [];
      }
      seatsByRow[row]!.add(seat);
    }

    final rows = seatsByRow.keys.toList()..sort();

    return Column(
      children: rows.map((row) {
        final seatsInRow = seatsByRow[row]!;
        return Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('$row행', style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 12)),
              const SizedBox(height: 4),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: seatsInRow.map((seat) {
                  final seatNo = seat['seatNo'] as String? ?? '';
                  final stCd = seat['stCd'] as String? ?? 'A';
                  final isSelected = widget.session.selectedSeats.contains(seatNo);
                  final isMine = seat['mine'] as bool? ?? false;

                  Color bgColor;
                  Color textColor;
                  if (isSelected) {
                    bgColor = Colors.blue;
                    textColor = Colors.white;
                  } else if (stCd == 'S') {
                    bgColor = Colors.red[300]!;
                    textColor = Colors.white;
                  } else if (stCd == 'H') {
                    bgColor = Colors.grey[300]!;
                    textColor = Colors.grey[700]!;
                  } else if (isMine) {
                    bgColor = Colors.cyan[300]!;
                    textColor = Colors.white;
                  } else {
                    bgColor = Colors.green[200]!;
                    textColor = Colors.white;
                  }

                  return SizedBox(
                    width: 44,
                    height: 44,
                    child: Material(
                      color: bgColor,
                      borderRadius: BorderRadius.circular(4),
                      child: InkWell(
                        onTap: () => _toggleSeatSelection(seatNo, stCd),
                        child: Center(
                          child: Text(
                            seatNo.split('-').last,
                            style: TextStyle(
                              color: textColor,
                              fontWeight: FontWeight.bold,
                              fontSize: 11,
                            ),
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        );
      }).toList(),
    );
  }

  Widget _buildProductTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Expanded(
            child: _isLoadingPrd
                ? const Center(child: CircularProgressIndicator())
                : _products.isEmpty
                    ? Center(
                        child: Text(
                          '판매 가능한 상품이 없습니다.',
                          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                color: Colors.grey[600],
                              ),
                        ),
                      )
                    : ListView.builder(
                        itemCount: _products.length,
                        itemBuilder: (context, index) {
                          final prd = _products[index];
                          final prdNo = prd['prdNo'] as String? ?? '';
                          final qty = _selectedProducts[prdNo] ?? 0;
                          return _ProductCard(
                            prdNm: prd['prdNm'] as String? ?? '',
                            prdPrc: prd['prdPrc'] as int? ?? 0,
                            remainQty: prd['remainQty'] as int? ?? 0,
                            currentQty: qty,
                            onQtyChange: (newQty) {
                              setState(() {
                                if (newQty > 0) {
                                  _selectedProducts[prdNo] = newQty;
                                } else {
                                  _selectedProducts.remove(prdNo);
                                }
                              });
                            },
                          );
                        },
                      ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton(
              onPressed: _selectedProducts.isEmpty ? null : _confirmOrder,
              child: const Text('주문 확정'),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _ttlTimer?.cancel();
    _tabController.dispose();
    super.dispose();
  }
}

class _ProductCard extends StatelessWidget {
  final String prdNm;
  final int prdPrc;
  final int remainQty;
  final int currentQty;
  final Function(int) onQtyChange;

  const _ProductCard({
    required this.prdNm,
    required this.prdPrc,
    required this.remainQty,
    required this.currentQty,
    required this.onQtyChange,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(prdNm, style: const TextStyle(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Text(
                    '${(prdPrc).toString().replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]},')}원',
                    style: TextStyle(color: Colors.grey[600], fontSize: 12),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '남은 수량: $remainQty개',
                    style: TextStyle(color: Colors.grey[500], fontSize: 11),
                  ),
                ],
              ),
            ),
            Column(
              children: [
                SizedBox(
                  width: 100,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      SizedBox(
                        width: 28,
                        height: 28,
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: EdgeInsets.zero,
                          ),
                          onPressed: currentQty > 0 ? () => onQtyChange(currentQty - 1) : null,
                          child: const Text('-'),
                        ),
                      ),
                      SizedBox(
                        width: 44,
                        child: Center(child: Text('$currentQty')),
                      ),
                      SizedBox(
                        width: 28,
                        height: 28,
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: EdgeInsets.zero,
                          ),
                          onPressed: currentQty < remainQty ? () => onQtyChange(currentQty + 1) : null,
                          child: const Text('+'),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

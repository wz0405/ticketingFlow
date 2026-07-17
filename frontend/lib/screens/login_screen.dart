import 'dart:math';
import 'package:flutter/material.dart';
import '../session.dart';
import '../api_client.dart';

class LoginScreen extends StatefulWidget {
  final Session session;

  const LoginScreen({Key? key, required this.session}) : super(key: key);

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _usrNmController = TextEditingController();
  final TextEditingController _loadCntController = TextEditingController(text: '300');
  final ApiClient _apiClient = ApiClient();
  bool _isLoading = false;

  // 데모 부하 패널 상태
  List<Map<String, dynamic>> _schds = [];
  String? _selSchdNo;
  bool _demoLoading = false;

  @override
  void initState() {
    super.initState();
    _apiClient.init();
  }

  /// 원클릭 체험: 가상 대기자 300명을 먼저 줄 세우고, 게스트로 로그인해
  /// 그 뒤(301번째)에 서서 대기열이 소화되는 과정을 바로 보여준다.
  Future<void> _handleQuickDemo() async {
    setState(() => _isLoading = true);
    try {
      await _loadSchds();
      final schdNo = _selSchdNo;
      if (schdNo == null || schdNo.isEmpty) {
        _showError('진행 중인 회차가 없습니다.');
        return;
      }

      // 가상 대기자 먼저 투입 — 실패해도 체험 흐름은 계속 진행
      // 100명 ≈ 15~20초 완주(면접관 인내심 배려). 더 보고 싶으면 대기 화면의 +100 버튼
      await _apiClient.demoLoad(schdNo, 100);

      final guestNm = '게스트${1000 + Random().nextInt(9000)}';
      final login = await _apiClient.usrLogin(guestNm);
      if (!mounted) return;
      if (!login.isSuccess || login.data == null) {
        _showError(login.rsltMsg);
        return;
      }
      _apiClient.authToken = login.data!['accessToken'] as String?;
      widget.session.setLogin(
        login.data!['usrId'] as String? ?? '',
        login.data!['usrNm'] as String? ?? '',
      );
      widget.session.enterQueue(schdNo);
    } catch (e) {
      _showError('체험 시작 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _handleLogin() async {
    final usrNm = _usrNmController.text.trim();
    if (usrNm.isEmpty) {
      _showError('사용자명을 입력해주세요.');
      return;
    }

    setState(() => _isLoading = true);
    try {
      final response = await _apiClient.usrLogin(usrNm);
      if (!mounted) return;

      if (response.isSuccess && response.data != null) {
        final usrId = response.data!['usrId'] as String? ?? '';
        final usrNm2 = response.data!['usrNm'] as String? ?? '';
        _apiClient.authToken = response.data!['accessToken'] as String?;
        widget.session.setLogin(usrId, usrNm2);
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('로그인 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: Colors.red[700]),
    );
  }

  Future<void> _loadSchds() async {
    if (_schds.isNotEmpty) return;
    try {
      final response = await _apiClient.eventList();
      if (!mounted) return;
      if (response.isSuccess && response.data != null) {
        final list = (response.data!['list'] as List? ?? []).cast<Map<String, dynamic>>();
        setState(() {
          _schds = list;
          if (list.isNotEmpty) _selSchdNo = list.first['schdNo'] as String?;
        });
      }
    } catch (_) {
      // 데모 패널 부가 기능이므로 실패해도 로그인 흐름은 막지 않는다
    }
  }

  Future<void> _handleDemoLoad() async {
    final schdNo = _selSchdNo;
    final count = int.tryParse(_loadCntController.text.trim()) ?? 0;
    if (schdNo == null || schdNo.isEmpty) {
      _showError('회차를 선택해주세요.');
      return;
    }
    if (count < 1 || count > 1000) {
      _showError('인원수는 1~1000 사이로 입력해주세요.');
      return;
    }

    setState(() => _demoLoading = true);
    try {
      final response = await _apiClient.demoLoad(schdNo, count);
      if (!mounted) return;
      if (response.isSuccess && response.data != null) {
        final total = response.data!['totalWaiting'];
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('가상 대기자 $count명 투입 완료 (현재 대기 $total명) — 로그인해서 줄을 서보세요!'),
            backgroundColor: Colors.green[700],
          ),
        );
      } else {
        _showError(response.rsltMsg);
      }
    } catch (e) {
      _showError('부하 투입 실패: $e');
    } finally {
      if (mounted) setState(() => _demoLoading = false);
    }
  }

  String _schdLabel(Map<String, dynamic> schd) {
    final evntNm = schd['evntNm'] as String? ?? '';
    final schdDt = (schd['schdDt'] as String? ?? '').replaceAll('T', ' ');
    final dt = schdDt.length >= 16 ? schdDt.substring(0, 16) : schdDt;
    return '$evntNm · $dt';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 560),
            child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // 로고 영역
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primary,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Icon(
                  Icons.confirmation_num,
                  size: 40,
                  color: Theme.of(context).colorScheme.onPrimary,
                ),
              ),
              const SizedBox(height: 24),
              Text(
                'TicketingFlow',
                style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 8),
              Text(
                '대규모 트래픽 대기열 시스템 데모 · Redis / Lua / Streams',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Colors.grey[600],
                    ),
              ),
              const SizedBox(height: 32),
              // 원클릭 체험 — 이 데모의 메인 진입점
              SizedBox(
                width: double.infinity,
                height: 56,
                child: FilledButton.icon(
                  onPressed: _isLoading ? null : _handleQuickDemo,
                  icon: _isLoading
                      ? const SizedBox(
                          width: 22,
                          height: 22,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.rocket_launch),
                  label: const Text(
                    '바로 체험하기 — 대기열 100명 뚫고 입장',
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '클릭 한 번으로 가상 대기자 100명 뒤에 줄을 섭니다.\n'
                '순번이 불규칙하게 빠지는 것(정체·버스트)까지가 설계입니다.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.grey[600]),
              ),
              const SizedBox(height: 28),
              Row(
                children: [
                  const Expanded(child: Divider()),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: Text('또는 이름으로 입장',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Colors.grey[500])),
                  ),
                  const Expanded(child: Divider()),
                ],
              ),
              const SizedBox(height: 20),
              // 입력 폼
              TextField(
                controller: _usrNmController,
                enabled: !_isLoading,
                decoration: InputDecoration(
                  labelText: '사용자명',
                  hintText: '이름을 입력하세요',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                  prefixIcon: const Icon(Icons.person),
                ),
                onSubmitted: (_) => _isLoading ? null : _handleLogin(),
              ),
              const SizedBox(height: 24),
              // 로그인 버튼
              SizedBox(
                width: double.infinity,
                height: 48,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _handleLogin,
                  child: _isLoading
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                          ),
                        )
                      : const Text('로그인'),
                ),
              ),
              const SizedBox(height: 32),
              // 데모: 대기열 부하 발생 — 가상 대기자를 투입해두고 로그인하면
              // 그 뒤에 줄 서서 입장 처리가 소화되는 과정을 볼 수 있다
              Card(
                elevation: 0,
                color: Colors.grey.withOpacity(0.08),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                  side: BorderSide(color: Colors.grey.withOpacity(0.3)),
                ),
                child: ExpansionTile(
                  leading: const Icon(Icons.groups),
                  title: const Text('데모 · 대기열 부하 발생'),
                  subtitle: const Text('가상 대기자를 투입한 뒤 로그인하면 그 뒤에 줄을 섭니다'),
                  onExpansionChanged: (open) {
                    if (open) _loadSchds();
                  },
                  childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  children: [
                    DropdownButtonFormField<String>(
                      value: _selSchdNo,
                      isExpanded: true,
                      decoration: InputDecoration(
                        labelText: '대상 회차',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                      ),
                      items: _schds
                          .map((s) => DropdownMenuItem(
                                value: s['schdNo'] as String?,
                                child: Text(_schdLabel(s), overflow: TextOverflow.ellipsis),
                              ))
                          .toList(),
                      onChanged: (v) => setState(() => _selSchdNo = v),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _loadCntController,
                            enabled: !_demoLoading,
                            keyboardType: TextInputType.number,
                            decoration: InputDecoration(
                              labelText: '인원수 (최대 1000)',
                              border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        SizedBox(
                          height: 48,
                          child: ElevatedButton.icon(
                            onPressed: _demoLoading ? null : _handleDemoLoad,
                            icon: _demoLoading
                                ? const SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(strokeWidth: 2),
                                  )
                                : const Icon(Icons.bolt),
                            label: const Text('부하 투입'),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 40),
              _buildInfoImage(context, '시스템 아키텍처', 'arch.png'),
              const SizedBox(height: 28),
              _buildInfoImage(context, '부하테스트 — k6 · 200 VU · 120s (개선 전 → 후)', 'loadtest.png'),
              const SizedBox(height: 16),
            ],
            ),
          ),
        ),
      ),
    );
  }

  /// 랜딩 하단 정보 이미지 섹션. 파일이 없으면 조용히 숨긴다.
  Widget _buildInfoImage(BuildContext context, String title, String file) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 12),
        ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Image.network(
            file,
            width: double.infinity,
            fit: BoxFit.fitWidth,
            errorBuilder: (context, error, stack) => const SizedBox.shrink(),
          ),
        ),
      ],
    );
  }

  @override
  void dispose() {
    _usrNmController.dispose();
    _loadCntController.dispose();
    super.dispose();
  }
}

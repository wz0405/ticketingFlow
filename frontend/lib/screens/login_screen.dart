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
  final ApiClient _apiClient = ApiClient();
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _apiClient.init();
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
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
                '쉽고 빠른 티켓 예매 서비스',
                style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Colors.grey[600],
                    ),
              ),
              const SizedBox(height: 48),
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
            ],
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _usrNmController.dispose();
    super.dispose();
  }
}

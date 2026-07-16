import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiResponse<T> {
  final String rsltCd;
  final String rsltMsg;
  final T? data;

  ApiResponse({
    required this.rsltCd,
    required this.rsltMsg,
    this.data,
  });

  bool get isSuccess => rsltCd == '0000';

  factory ApiResponse.fromJson(Map<String, dynamic> json, T Function(dynamic)? dataParser) {
    return ApiResponse(
      rsltCd: json['rsltCd'] as String? ?? '9999',
      rsltMsg: json['rsltMsg'] as String? ?? '',
      data: json['data'] != null && dataParser != null ? dataParser(json['data']) : null,
    );
  }
}

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();

  factory ApiClient() {
    return _instance;
  }

  ApiClient._internal();

  late String _baseUrl;
  final http.Client _httpClient = http.Client();

  // 로그인 시 발급받은 신원 토큰. 이후 모든 요청에 Bearer로 실린다.
  String? authToken;

  void init({String? baseUrl}) {
    if (baseUrl != null && baseUrl.isNotEmpty) {
      _baseUrl = baseUrl;
    } else {
      // 빈 값이면 same-origin 상대경로 사용
      _baseUrl = '';
    }
  }

  String _getUrl(String bizNm) {
    if (_baseUrl.isEmpty) {
      return '/api/$bizNm';
    }
    return '$_baseUrl/api/$bizNm';
  }

  Future<ApiResponse<T>> post<T>(
    String bizNm,
    Map<String, dynamic> body,
    T Function(dynamic)? dataParser,
  ) async {
    try {
      final url = _getUrl(bizNm);
      final headers = {'Content-Type': 'application/json'};
      if (authToken != null) {
        headers['Authorization'] = 'Bearer $authToken';
      }
      final response = await _httpClient.post(
        Uri.parse(url),
        headers: headers,
        body: jsonEncode(body),
      ).timeout(const Duration(seconds: 30));

      // 응답에 charset이 없으면 http 패키지가 Latin-1로 디코딩해 한글이 깨진다.
      // bodyBytes를 UTF-8로 직접 디코딩한다.
      final json = jsonDecode(utf8.decode(response.bodyBytes)) as Map<String, dynamic>;
      return ApiResponse<T>.fromJson(json, dataParser);
    } catch (e) {
      return ApiResponse(
        rsltCd: '9999',
        rsltMsg: '네트워크 오류: $e',
      );
    }
  }

  // 편의 메서드들
  Future<ApiResponse<Map<String, dynamic>>> usrLogin(String usrNm) async {
    return post('usrLogin', {'usrNm': usrNm}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> eventList() async {
    return post('eventList', {}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> queueEnter(String schdNo, String usrId) async {
    return post('queueEnter', {'schdNo': schdNo, 'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> queueStatus(String schdNo, String usrId) async {
    return post('queueStatus', {'schdNo': schdNo, 'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> queueLeave(String schdNo, String usrId) async {
    return post('queueLeave', {'schdNo': schdNo, 'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> seatList(String schdNo, String usrId) async {
    return post('seatList', {'schdNo': schdNo, 'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> rsvHold(
    String schdNo,
    String usrId,
    String entryToken,
    List<String> seatNos,
  ) async {
    return post('rsvHold', {
      'schdNo': schdNo,
      'usrId': usrId,
      'entryToken': entryToken,
      'seatNos': seatNos,
    }, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> rsvConfirm(
    String schdNo,
    String usrId,
    String entryToken,
    List<String> seatNos,
  ) async {
    return post('rsvConfirm', {
      'schdNo': schdNo,
      'usrId': usrId,
      'entryToken': entryToken,
      'seatNos': seatNos,
    }, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> rsvCancel(String rsvNo, String usrId) async {
    return post('rsvCancel', {'rsvNo': rsvNo, 'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> myRsv(String usrId) async {
    return post('myRsv', {'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> prdList(String schdNo) async {
    return post('prdList', {'schdNo': schdNo}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> ordConfirm(
    String schdNo,
    String usrId,
    String entryToken,
    List<Map<String, dynamic>> items,
  ) async {
    return post('ordConfirm', {
      'schdNo': schdNo,
      'usrId': usrId,
      'entryToken': entryToken,
      'items': items,
    }, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> ordCancel(String ordNo, String usrId) async {
    return post('ordCancel', {'ordNo': ordNo, 'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }

  Future<ApiResponse<Map<String, dynamic>>> myOrd(String usrId) async {
    return post('myOrd', {'usrId': usrId}, (data) => data as Map<String, dynamic>);
  }
}

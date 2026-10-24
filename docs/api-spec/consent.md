# 동의 API (ONB-01)

모두 로그인 필요 (ACCESS_TOKEN 쿠키). 응답은 공통 봉투 `{success, code, message, errorCode, data}`.

## 항목 (type)
| type | 필수 | 화면 |
|---|---|---|
| AGE_14 | ✅ | A-01 만 14세 이상 |
| TERMS | ✅ | A-01 이용약관 (N-03) |
| PRIVACY | ✅ | A-01 개인정보 (N-02) |
| SENSITIVE | ✅ | A-01 민감정보 (N-04) |
| MARKETING | | A-01 광고성 정보 받기 · 마이에서 끄기 |
| AI_PHOTO | | F-02 첫 사진 |
| AI_CHAT_RECORDS | | 챗봇이 내 기록을 읽어도 되는지 |
| OVERSEAS_TRANSFER | | Q-01 국외 이전 (N-05) |

## POST /api/me/consents — 저장
바꿀 항목만 보낸다. A-01은 5개, F-02는 AI_PHOTO 하나.
```json
{"items":[{"type":"AGE_14","granted":true},{"type":"TERMS","granted":true},{"type":"PRIVACY","granted":true},{"type":"SENSITIVE","granted":true},{"type":"MARKETING","granted":false}]}
```
200 → data = 아래 GET과 같은 모양

## GET /api/me/consents — 지금 상태
```json
{"completed":true,"items":[{"type":"AGE_14","required":true,"granted":true,"version":"v1","occurredAt":"2026-10-23T11:00:00Z"}, "... 8개"]}
```
한 번도 안 고른 항목: granted=false, version=null, occurredAt=null

## GET /api/me — consentCompleted 추가
false면 A-01로 보낸다 (가입만 하고 동의 화면에서 나간 사람).

## 에러
| errorCode | 상태 | 언제 |
|---|---|---|
| USER_40001 | 400 | 필수 항목(AGE_14·TERMS·PRIVACY·SENSITIVE)을 false로 보냄 |
| GLOBAL_40001 | 400 | items가 비었거나 type·granted가 없음, 모르는 type 이름 |
| AUTH_40101 / 40102 | 401 | 토큰 없음 / 만료 |
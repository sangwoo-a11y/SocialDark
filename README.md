# SocialDark for Android

Instagram과 YouTube를 필요한 만큼 사용하는 Android 앱입니다. Android 네이티브 화면과 내장 WebView를 사용하며, 별도 계정이나 서버 없이 동작합니다. Android 8.0(API 26) 이상을 지원합니다.

## 기능

- Instagram: 릴스, 탐색, 홈 게시물, 스토리, 댓글 숨기기
- YouTube: Shorts, 홈 피드, 추천 동영상, 댓글 숨기기 및 연속 자동재생 끄기
- 서비스별 필터 켜기/끄기와 설정 자동 저장
- 5·15·25·45분 집중 세션: 집중 필터를 임시로 모두 켜고 종료 시 기존 설정 복원
- 서비스별 하루 사용 한도(15·30·60분), 날짜별 사용 시간과 최근 7일 기록
- 웹 동영상 전체 화면, 뒤로 가기, 연결 오류 시 재시도
- 설정·기록은 기기에만 저장하고 기록 삭제 지원

**필터와 한도는 SocialDark 안에서 연 웹 화면에만 적용됩니다. 공식 Instagram·YouTube 앱이나 다른 브라우저를 제어하지 않습니다.** 접근성·VPN·알림 권한은 사용하지 않습니다. 앱이 백그라운드에 있는 시간은 사용 기록에 포함되지 않습니다. 집중 타이머는 앱을 닫아도 종료 시각을 유지하며 별도 알림은 보내지 않습니다.

## 실행 및 설치

Android Studio에서 프로젝트 루트를 열고 JDK 17, Android SDK Platform 35, Build Tools 35.0.0을 사용합니다. SDK 경로는 `ANDROID_HOME` 또는 Git에서 제외되는 `local.properties`에 설정합니다.

```sh
./gradlew assembleDebug
./gradlew installDebug  # USB 디버깅이 허용된 연결 기기 또는 에뮬레이터
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. 휴대폰으로 복사하여 설치할 수도 있습니다. 이는 개발용 서명이며 스토어 배포용 서명은 포함하지 않습니다. GitHub Actions에서도 검증 후 APK를 빌드 아티팩트로 제공합니다.

앱에서 서비스를 열고 해당 서비스 웹 화면에서 로그인합니다. 필터 설정에서 항목을 바꾼 뒤 서비스를 다시 열면 적용됩니다. 서비스 로그인 정책 때문에 일부 인증 방식이 WebView에서 거부될 수 있습니다. 이를 우회하거나 사용자 비밀번호를 대신 받지 않습니다.

## 검증

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # 연결 기기 또는 에뮬레이터에서 네이티브 테스트
npm ci
npx playwright install chromium  # 시스템 Chromium이 없을 때
npm test
```

`CHROMIUM_PATH`로 테스트 브라우저 실행 파일을 지정할 수 있습니다. npm 의존성은 웹 필터 테스트용이며 Android 앱에는 포함되지 않습니다.

- JUnit: 도메인·HTTPS·외부 링크 검증과 차단 경로 처리
- Android 계측 테스트: 화면 전환, 설정 보존, 집중 세션, 사용 한도, WebView 설정
- Playwright: 모바일 웹 DOM 샘플에서 필터 동작, SPA 이동, 동적 콘텐츠, 원복, 허용되지 않은 출처 보호

Codex Cloud에서는 `bash scripts/setup-cloud.sh`로 검증된 JDK·SDK와 테스트 의존성을 준비한 다음, `python3 scripts/cloud-build.py <Gradle 작업>`으로 빌드합니다. 도구와 캐시는 `/workspace/toolchains`에 보관합니다. `bash scripts/start-emulator.sh`는 API 35 소프트웨어 에뮬레이터를 실행하며, 최초 실행 시 ADB의 기본 사용자 설정 디렉터리 생성 권한이 필요할 수 있습니다. Android Studio나 일반 개발 머신에서는 표준 `./gradlew` 명령을 사용하세요.

DOM 샘플 테스트는 실제 계정으로 두 서비스 전체를 검증한 결과가 아닙니다. 실제 기기에서는 로그인, 메시지, 검색, 재생·전체 화면, 실제 웹 DOM의 필터 적용을 별도로 확인해야 합니다.

## 구조와 개인정보

```text
app/src/main/java/app/socialdark/
  MainActivity.java       네이티브 UI, WebView, 생명주기와 사용 시간
  Preferences.java        필터·집중 세션·한도·기록
  NavigationPolicy.java   HTTPS 도메인과 차단 경로 검증
  Site.java               지원 서비스
app/src/main/assets/filters.js  페이지 내 CSS·DOM 필터
app/src/test/             Java·Android 테스트
app/src/androidTest/      실제 Android 런타임 계측 테스트
tests/                   웹 필터 회귀 테스트
```

원격 페이지에 Android JavaScript 인터페이스를 노출하지 않습니다. 파일 접근·HTTP·혼합 콘텐츠를 허용하지 않고 SSL 오류를 무시하지 않습니다. 외부 링크는 사용자 확인 후 외부 브라우저로 엽니다. 웹 페이지의 비밀번호·메시지·동영상 내용은 앱 서버로 전송하지 않으며 앱 서버 자체가 없습니다. 각 웹 서비스의 쿠키·개인정보 처리방침은 별도로 적용됩니다. 앱 데이터는 Android 자동 백업에서 제외합니다.

## 현재 한계 및 참고

- SocialLite(`https://sociallite.app/`)를 참고 대상으로 요청받았습니다. 구현 시 클라우드 네트워크 정책으로 원본 사이트를 열 수 없어, 원본 화면·전체 기능 목록과의 일치 여부는 확인하지 못했습니다. 현재 디자인은 독자적인 다크/민트 UI입니다.
- 필터는 웹 서비스의 공개 DOM 구조·링크를 기준으로 합니다. 서비스의 UI 실험·언어·업데이트로 일부 필터가 작동하지 않을 수 있습니다. 광고 제거·완전한 접근 차단을 보장하는 앱은 아닙니다.
- WebView에서 문서가 로드된 뒤 필터를 주입하므로 콘텐츠가 잠깐 보일 수 있습니다.
- 집중 세션과 한도는 자율 관리용입니다. 기기 시각 변경이나 앱 데이터 초기화까지 방지하지 않습니다.
- 카메라·마이크·파일 업로드·다운로드·푸시 알림·백그라운드 재생은 이번 버전의 지원 범위에 포함하지 않습니다.

# 기술 과제 - REST API 개발
## 목차
- [개발 환경](#개발-환경)
- [빌드 및 실행하기](#빌드-및-실행하기)
- [기능 요구사항](#기능-요구사항)
- [폴더 구조](#폴더-구조)
- [데이터베이스 접근방법](#데이터베이스-접근방법)
- [API](#api)
- [결과](#결과)

<br/><br/>

## 개발 환경
- 기본 환경
    - IDE: IntelliJ IDEA Ultimate
    - OS: Mac
    - GIT
- Server
    - Java 17
    - Spring Boot 3.4.2
    - Spring Data JPA
    - H2 Database
    - Gradle 8.13
    - JUnit 5
- 라이브러리 및 도구
    - Lombok
    - Swagger/OpenAPI (springdoc-openapi-starter-webmvc-ui 2.3.0)
    - Apache POI 5.2.3 (Excel 파일 처리)
    - OpenCSV 5.9 (CSV 파일 처리)
    - Selenium 4.1.2 (웹 자동화)

<br/><br/>

## 빌드 및 실행하기
### 터미널 환경
- Git, Java 는 설치되어 있다고 가정한다.

```
$ git clone https://github.com/juhwano/backend-challenge.git
$ cd backend-challenge
./gradlew clean build
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

<br/><br/>

## 기능 요구사항
### 필수사항
- POST 요청을 처리하는 REST API 개발
- 공공데이터포털 사이트에서 CSV,XLS 파일 다운로드 및 데이터 필터링
- 공공데이터포털 API/공공주소 API를 통한 추가 데이터 조회
- 데이터 정제 후 DB에 저장
- 테스트코드 작성
  
### 고려사항
- API 출처 변경에 대응 가능한 유연한 설계
- 저장소 변경에 대응 가능한 추상화된 저장소 계층
- 멀티쓰레드를 활용한 병렬 처리로 성능 최적화
- 동시성 문제를 해결하는 안전한 구현
- 유지보수가 용이한 로깅 처리


<br/><br/>

## 폴더 구조

```plaintext
├── .git/                      # Git 저장소
├── .gitattributes             # Git 속성 파일
├── .gitignore                 # Git 무시 파일 목록
├── README.md                  # 프로젝트 설명 문서
├── build.gradle               # Gradle 빌드 설정
├── gradle/                    # Gradle 래퍼 디렉토리
│   └── wrapper/               # Gradle 래퍼 파일
├── gradlew                    # Gradle 래퍼 실행 스크립트 (Unix)
├── gradlew.bat                # Gradle 래퍼 실행 스크립트 (Windows)
├── settings.gradle            # Gradle 프로젝트 설정
└── src/                       # 소스 코드
    ├── main/                  # 메인 소스 코드
    │   ├── java/
    │   │   └── com/antock/backend/
    │   │       ├── BackendApplication.java       # 애플리케이션 진입점
    │   │       ├── client/                       # 외부 API 클라이언트
    │   │       ├── config/                       # 설정 클래스 (Bean 등록 등)
    │   │       ├── controller/                   # 요청 처리 컨트롤러
    │   │       ├── domain/                       # 도메인 모델 및 엔티티
    │   │       ├── dto/                          # 데이터 전송 객체 (Request, Response)
    │   │       ├── repository/                   # DB 접근 레이어
    │   │       └── service/                      # 비즈니스 로직 처리
    │   └── resources/
    │       └── application.yml                   # 애플리케이션 설정 파일
    └── test/
        └── java/
            └── com/antock/backend/
                ├── integration/                  # 통합 테스트
                └── service/                      # 서비스 단위 테스트
```




<br/><br/>

## 데이터베이스 접근방법
- Access URL: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- Driver Class: `org.h2.Driver`
- JDBC URL:  `jdbc:h2:mem:antockdb`
- User Name: `sa`

<br/><br/>

## API
[Request]
- URL: `http://localhost:8080/v1/business`
- Method: `POST`
- Body:
  ```json
  {
    "city": "경상남도",
    "district": "의령군"
  }
  ```
- 설명: 위 요청은 [공정거래위원회 사업자등록현황](https://www.ftc.go.kr/www/selectBizCommOpenList.do?key=255#n) 사이트에서 시/도 선택 (예: `경상남도`)과 전체 (예: `의령군`)에 해당하는 값을 넣어서 요청합니다.

[Response]
  ```json
  {
    "processedCount": 81,
    "message": "데이터가 성공적으로 처리되었습니다."
  }
  ```
  - processedCount: 처리된 데이터 항목 수
  - message: 처리 결과 메시지

- 서버는 해당 시/도와 군/구에 해당하는 사업자 정보를 처리한 후, 처리된 데이터 수와 메시지를 반환합니다.

<br/><br/>

## 결과
![스크린샷 2025-04-05 23 23 38](https://github.com/user-attachments/assets/908b89a2-110a-4ee6-ad87-dc2fadaea092)


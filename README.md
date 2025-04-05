# 기술 과제

### 프로젝트 구조

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

### 아키텍처 (계층 구조)
1. 컨트롤러 계층 (Controller Layer)
   - 클라이언트 요청을 처리하고 응답을 반환
   - REST API 엔드포인트 정의
2. 서비스 계층 (Service Layer)
   - 비즈니스 로직 처리
   - 트랜잭션 관리
3. 저장소 계층 (Repository Layer)
   - 데이터 접근 로직
   - JPA 기반 데이터 조작
4. 도메인 계층 (Domain Layer)
   - 비즈니스 엔티티 정의
5. 클라이언트 계층 (Client Layer)
   - 외부 API 통신 담당

<br/><br/>

### 데이터베이스 접근방법 (H2)
- Access URL: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- Driver Class: `org.h2.Driver`
- JDBC URL:  `jdbc:h2:mem:antockdb`
- User Name: `sa`

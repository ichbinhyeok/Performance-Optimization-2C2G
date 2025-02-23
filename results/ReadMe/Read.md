# 🚀 서버 성능 최적화 프로젝트

<div align="center">

[![Optimization Results](https://img.shields.io/badge/Performance-98.2%25%20Improved-brightgreen)]()
[![Response Time](https://img.shields.io/badge/Response%20Time-176ms-blue)]()
[![Environment](https://img.shields.io/badge/Environment-Docker%202C2G-orange)]()

</div>

> **Docker(2C2G) 환경에서 10초 걸리던 API 응답을 176ms까지 개선한 성능 최적화 프로젝트**  
> *실제 프로덕션 환경의 제약을 고려하여 데이터베이스 최적화, 캐싱 전략, 커넥션 풀 관리 등 다양한 기술을 적용해 API 성능을 획기적으로 개선했습니다.*

## 📑 목차
1. [💭 Background & Motivation](#-background--motivation)
2. [🔧 프로젝트 스펙](#-프로젝트-스펙--환경-구성)
3. [💡 최적화 성과](#-최적화-성과)
4. [🎓 결론 및 배운점](#-결론-및-배운점)
5. [🛠 레벨별 최적화 전략](#-레벨별-최적화-전략)
6. [📈 단계별 성능 개선 과정](#-단계별-성능-개선-과정)

## 💭 Background & Motivation

> "실제 서비스에서는 비용과 자원의 제약이 있는데, 이런 환경에서 어떻게 성능을 최적화할 수 있을까?"

서비스 성능 개선에서 가장 중요한 것은 실제 환경에서의 제약사항을 고려한 최적화입니다.

## 🔧 프로젝트 스펙 & 환경 구성

> 실제 프로덕션 환경을 고려하여 제한된 컴퓨팅 자원(2C2G)에서 최적의 성능을 달성하기 위한 환경을 구성했습니다.

| 분야 | 상세 스펙 |
|------|-----------|
| **Backend & DevOps** | ![Java](https://img.shields.io/badge/Java-007396?style=flat&logo=java) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat&logo=spring-boot) ![JPA](https://img.shields.io/badge/JPA-59666C?style=flat) ![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker) |
| **Database** | ![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=flat&logo=mariadb) ![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat&logo=redis) |
| **Infrastructure** | App(2C/2G), DB(2C/2G), Redis(1C/1G) |
| **성능 목표** | • 응답시간 300ms 이하<br>• 동시접속 200명<br>• TPS 25+ |
| **테스트 환경** | • JMeter: 200 스레드, 30초 램프업, 20회 반복<br>• VisualVM: CPU/Memory 프로파일링 |
| **테스트 시나리오** | 로그인 → 인기글 목록/상세 → 댓글/대댓글 조회 → 좋아요 → 댓글 작성 |

## 💡 최적화 성과

> 9차에 걸친 단계별 최적화를 통해 전체 API 응답시간을 98.2% 이상 개선하고, 에러율을 0%로 낮추는데 성공했습니다.

### 📊 API별 성능 개선 결과 상세

| API | 최적화 전 |  | 최적화 후 |  | 개선율(%) |  |
|---|---:|---:|---:|---:|---:|---:|
| | AVG(ms) | TPS | AVG(ms) | TPS | AVG | TPS |
| 로그인 | 15,817 | 2.53 | 603 | 6.63 | 96.2 | 161.3 |
| 인기 게시글 목록 | 23,691 | 2.53 | 61 | 65.57 | 99.7 | 2,491.7 |
| 게시글 조회 | 4,582 | 2.54 | 82 | 48.78 | 98.2 | 1,911.0 |
| 댓글 페이징 조회 | 3,334 | 2.56 | 122 | 32.79 | 96.3 | 1,175.0 |
| 대댓글 전체 조회 | 3,337 | 0.23 | 218 | 18.35 | 93.5 | 7,870.0 |
| 포스트 좋아요 | 3,364 | 1.30 | 134 | 14.93 | 96.0 | 1,045.4 |
| 포스트 좋아요 취소 | 2,653 | 0.65 | 168 | 5.95 | 93.7 | 817.0 |
| 루트 댓글 작성 | 3,241 | 0.39 | 111 | 5.41 | 96.6 | 1,282.1 |
| 대댓글 작성 | 3,337 | 0.23 | 181 | 2.66 | 94.6 | 1,065.2 |

## 🎓 결론 및 배운점

> 이번 프로젝트를 통해 성능 최적화는 단순한 코드 개선이 아닌, 시스템 전반에 대한 깊은 이해와 데이터 기반의 접근이 필요하다는 것을 배웠습니다.

### 1. "추측하지 말고 측정하자"
- 모든 성능 이슈는 추측이 아닌 데이터로 접근
- VisualVM과 애플리케이션 로그를 통한 구체적인 수치 확인의 중요성
- 예상과 다른 병목 포인트들을 발견하며 깨달은 측정의 가치

### 2. 리소스 관리는 단순한 숫자가 아니다
- "더 많은 쓰레드 = 더 빠른 성능"이라는 오해
- 처음에는 1요청 당 1쓰레드로 설계했지만, 웨이팅 커넥션 증가로 인한 성능 저하 경험
- 결국 API 특성에 따른 분리가 중요함을 깨달음 (로그인 4개, 일반 API 50개)

### 3. CPU 바운드와 I/O 바운드의 차이
- 로그인 API의 극단적 성능 저하를 통해 CPU 바운드 작업의 특성 이해
- 컨텍스트 스위치 비용이 생각보다 크다는 것을 경험
- 결과적으로 로그인용 쓰레드 풀 분리로 전체 시스템 성능 개선

### 4. JPA N+1과의 싸움
- 처음에는 연관관계 매핑에만 신경썼지만, N+1이 성능의 가장 큰 발목
- Fetch Join으로 해결되는 것과 안되는 것(Collection)을 구분하는 법 학습
- batch_fetch_size 값 조정을 통해 한번에 가져오는 데이터 양 최적화

### 5. 계층형 쿼리와 CTE의 발견
- 대댓글 조회 시 재귀 호출로 인한 성능 저하 고민
- CTE를 통해 계층형 데이터를 효율적으로 조회하는 방법 학습
- 쿼리 한 번으로 전체 계층 데이터를 가져오는 즐거움

### 6. 전체를 보는 안목
- 단순히 코드 레벨의 최적화가 아닌, 시스템 전반을 이해하는 것의 중요성
- DB, WAS, Network 각 레이어별 병목 지점을 찾아내는 능력이 중요
- 하나의 개선이 다른 부분에 미치는 영향을 항상 고려해야 함

## 🔍 트러블 슈팅 & 기술 검증

> 성능 개선 과정에서 마주친 다양한 문제들과 이를 해결하기 위해 진행한 기술 검증들을 정리했습니다.

1. [📌 JPA Set 컬렉션과 extractHashCode](../Troubleshooting/JPA%20Set%20컬렉션과%20extractHashCode.md)
2. [📌 로그인 API와 일반API 분리](../Troubleshooting/로그인%20API와%20일반API%20분리.md)
3. [📌 JPA N+1 문제란](../Troubleshooting/JPA%20N+1%20문제란.md)
4. [📌 재귀 쿼리를 통한 N+1 문제해결](../Troubleshooting/재귀%20쿼리를%20통한%20N+1%20문제해결.md)
5. [📌 Batch Size를 통한 N+1 문제 해결](../Troubleshooting/Batch%20Size를%20통한%20N+1%20문제%20해결.md)
6. [📌 Fetch Join을 통한 N+1 문제 해결](../Troubleshooting/Fetch%20Join을%20통한%20N+1%20문제%20해결.md)
7. [📌 JPA 변경감지와 N+1 문제](../Troubleshooting/JPA%20변경감지와%20N+1%20문제.md)
8. [📌 JPA 직렬화와 N+1 문제](../Troubleshooting/JPA%20직렬화와%20N+1%20문제.md)
9. [📌 Spring Security와 N+1 문제](../Troubleshooting/Spring%20Security와%20N+1%20문제.md)

## 🛠 레벨별 최적화 전략

> 성능 개선을 위해 데이터베이스, 인프라스트럭처, 애플리케이션 각 레이어별로 다양한 최적화 전략을 적용했습니다.

### 1. Database Layer

#### Query 최적화
- 실행 계획 분석 및 불필요한 조인/서브쿼리 제거
- 페치 조인 활용으로 N+1 문제 해결
- CTE(Common Table Expression) 도입으로 재귀 쿼리 최적화

#### Redis 캐싱 전략
- 인기 게시글 및 고부하 쿼리 캐싱
- API 응답용과 캐시용 DTO 분리로 효율적인 캐시 운영

#### 인덱스 최적화
- PostLike, Comment 엔티티에 복합 인덱스 추가
- User 엔티티 username 필드 인덱스 추가

#### Batch 처리 최적화
- Hibernate batch_fetch_size (1000)
- Projection 적용으로 영속성 컨텍스트 부하 감소

### 2. Infrastructure Layer

#### Connection Pool 최적화
- HikariCP 설정 최적화
  - 최대 풀 사이즈 조정 (200 → 50)
  - 커넥션 타임아웃 설정 최적화
- 로그인 전용 커넥션 풀 분리로 부하 분산

#### Thread Pool 관리
- 로그인 전용 쓰레드 풀 (4 threads) 도입
- 일반 API 쓰레드 풀 (50 threads) 최적화

#### 시스템 설정
- 로그 레벨 조정 (DEBUG → INFO)으로 오버헤드 감소
- 프록시 객체 활용으로 불필요한 엔티티 조회 제거

### 3. Application Layer

#### DTO 최적화
- API 응답용과 캐시용 DTO 분리
- Jackson 어노테이션 최적화로 직렬화/역직렬화 성능 개선

#### 인증 로직 개선
- BCrypt 강도 조절 (10 → 8)로 CPU 부하 감소
- JWT 토큰 생성 시 필요 데이터만 전달
- 로그인 전용 DTO 도입으로 불필요한 데이터 로딩 방지

#### JPA 최적화
- N+1 문제 해결을 위한 fetch join 적용
- User-Role 관계 Lazy 로딩 전환
- 엔티티 연관관계
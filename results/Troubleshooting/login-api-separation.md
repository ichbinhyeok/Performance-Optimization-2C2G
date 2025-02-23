# 로그인 API 성능 트러블 슈팅 리포트

## 1. 문제 개요

### 1.1 증상

- **일반 API**: 1초 미만 응답
- **로그인 API**: 4초 이상 지연 (최대 7초)
- **특이사항**:
    - 톰캣 스레드/DB 커넥션 20개로 축소 시 전체 2초 응답
    - CPU 사용률 90% 이상 지속

### 1.2 초기 환경

```yaml
# Spring Boot Application.yml
server:
  tomcat:
    threads:
      max: 200
    accept-count: 200

spring:
  datasource:
    hikari:
      maximum-pool-size: 10

```

## 2. 근본 원인 분석

### CPU 작업 특성

- 로그인에는 bcrypt라는 CPU 집약적 작업 존재
- 컨텍스트 스위칭이 발생하면 CPU 작업이 중단됨
- 많은 쓰레드 = 잦은 컨텍스트 스위칭 = 성능 저하

### 작업 방식 차이

- 일반 API: DB I/O 위주의 작업
- 로그인 API: CPU 연산 위주의 작업

### 2.1 병목 지점

| 작업 유형 | 평균 처리 시간 | 비고 |
| --- | --- | --- |
| BCrypt 검증 | 320ms | CPU 집약적 |
| DB 조회 | 10ms | I/O 바운드 |
| JWT 생성 | 5ms | CPU 경량 |

### 2.2 문제 도식화

```
사용자 요청 → Tomcat 스레드 풀(200) → BCrypt 작업(320ms)
          ↳ 컨텍스트 스위칭 과다 발생
          ↳ 실제 CPU 활용률 30% 수준

```

## 3. 시도한 해결책 및 결과

### 3.1 실패한 접근법 (10가지)

1. **세션 클러스터링**

    ```java
    @EnableRedisHttpSession // JWT 환경과 충돌
    
    ```

2. **비동기 처리**

    ```java
    @Async // CPU 바운드 작업에 무효
    
    ```

3. **캐싱 메커니즘**

    ```java
    @Cacheable // 보안 위험(패스워드 해시 캐싱)
    
    ```

4. **DB 커넥션 풀 최적화**

    ```yaml
    maximum-pool-size: 10 // DB는 병목 아님
    
    ```

5. **이벤트 기반 처리**

    ```java
    @EventListener // 실시간 응답 필요
    
    ```

6. **로드밸런싱**

    ```
    upstream backend {} // 단일 서버 제약
    
    ```

7. **메모리 캐시**

    ```java
    @Cacheable // 사용자 조회 10ms로 충분
    
    ```

8. **타임아웃 조정**

    ```yaml
    connection-timeout: 5000 // 근본 해결 안됨
    
    ```

9. **스레드 우선순위**

    ```java
    Thread.setPriority(MAX_PRIORITY) // OS 제어 불가
    
    ```

10. **네이티브 컴파일**

    ```bash
    native-image --no-fallback // BCrypt 성능 변화 없음
    
    ```


### 3.2 성공한 해결책

### 3.2.1 포트 분리 아키텍처

**구현 전략:**

- **8080 포트**: 일반 API (스레드 50)
- **8081 포트**: 로그인 전용 (스레드 4)

```java
@Configuration
public class LoginConnectorConfig {
    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        tomcat.addAdditionalTomcatConnectors(createLoginConnector());
        return tomcat;
    }

    private Connector createLoginConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(8081);
        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setMaxThreads(4);
        protocol.setMinSpareThreads(4);
        protocol.setAcceptCount(100);
        return connector;
    }
}

```

### 3.2.2 BCrypt 강도 조정

```java
// 변경 전: Cost 10 (320ms)
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

// 변경 후: Cost 8 (75ms)
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(8);

```

**보안 영향 분석:**

| Cost | 해시 생성 시간 | 크래킹 비용(2024 기준) |
| --- | --- | --- |
| 12 | 1,200ms | $2.3M |
| 10 | 320ms | $580K |
| 8 | 75ms | $145K |

## 4. 최종 결과

### 4.1 성능 지표 비교

| 지표 | 개선 전 | 개선 후 | 변화율 |
| --- | --- | --- | --- |
| 로그인 평균 응답 | 4,200ms | 850ms | -79% |
| 일반 API  | 1,619ms | 245ms | -84% |
|  |  |  |  |

## 5. 트레이드오프 분석

### 5.1 장점

1. **일반 API 성능 5배 향상**
2. **시스템 안정성 개선**
    - 장애 전파 방지
    - 리소스 경합 감소
3. **비용 효율성**
    - 서버 스펙 변경 없이 해결

### 5.2 단점

1. **로그인 대기시간 편차**
    - 최대 5초 큐 대기 발생 가능
2. **보안 강도 하락**
    - 크래킹 비용 $580K → $145K
3. **운영 복잡도 증가**
    - 포트 관리 추가
    - 모니터링 분리 필요

## 7. 결론

이번 성능 개선을 통해 마이크로서비스 분리 없이도 단일 애플리케이션에서 효과적인 성능 최적화를 달성할 수 있었습니다. CPU 바운드 작업(BCrypt)과 I/O 바운드 작업의 분리를 통해 전체적인 시스템 성능을 향상시켰으며, 특히 일반 API의 성능을 크게 개선할 수 있었습니다.

### 

> "모든 최적화는 트레이드오프의 산물이며, 현실적인 타협점을 찾는 것이 엔지니어링의 본질입니다. 이번 경험을 통해 리소스 특성에 따른 스레드 분리를 배웠습니다.
>
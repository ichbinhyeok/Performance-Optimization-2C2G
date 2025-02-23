# JPA 직렬화와 N+1 문제

## 직렬화(Serialization)의 이해

### 직렬화의 정의와 필요성

- 직렬화는 자바의 메모리 상에 존재하는 객체를 바이트 단위의 데이터로 변환하는 과정
- 객체는 JVM의 메모리에만 존재하므로, 네트워크로 전송하거나 파일로 저장하려면 반드시 직렬화 과정이 필요
- 예시: JWT 토큰 생성, API 응답 생성, 캐시 저장 등

### 직렬화의 동작 원리

- Java의 리플렉션을 사용하여 객체의 모든 필드를 찾아냄
- 각 필드를 순회하면서 데이터를 읽음
- 필드가 다른 객체를 참조하고 있다면, 그 객체도 함께 직렬화

### JPA 엔티티와 직렬화

```java
@Entity
public class User implements Serializable {
    @Id
    private Long id;
    private String name;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Post> posts;  // 지연 로딩으로 설정

    // 직렬화 과정:
    // 1. id 필드 직렬화 -> 단순 값이므로 바로 처리
    // 2. name 필드 직렬화 -> 단순 값이므로 바로 처리
    // 3. posts 필드 직렬화 시도 -> 프록시 객체 발견
    // 4. 프록시를 실제 데이터로 변환하기 위해 DB 조회 발생
    // 5. 조회된 모든 Post 객체들도 직렬화 필요
    // 6. 각 Post 객체의 모든 필드에 대해 1~5 과정 반복
}

```

## 직렬화로 인한 N+1 문제 발생 사례

### 1. JWT 토큰 생성 시

```java
// 문제가 있는 코드
public String generateToken(User user) { // User 전체 객체 전달
    return Jwts.builder()
            .setSubject(String.valueOf(user.getId())) // 실제로는 ID만 사용
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
            .compact();
}

```

### 2. API 응답 생성 시

```java
@GetMapping("/users/{id}")
public User getUser(@PathVariable Long id) {  // Entity를 직접 반환
    return userRepository.findById(id).orElseThrow();
}

```

### 3. 캐시 저장 시

```java
@Cacheable(value = "users", key = "#id")
public User getUser(Long id) {  // Entity를 직접 캐시
    return userRepository.findById(id).orElseThrow();
}

```

## 직렬화로 인한 성능 문제 발생 원인

### 1. 객체 그래프 전체 탐색

- 직렬화는 객체의 모든 필드를 처리해야 함
- 연관 관계가 있는 객체들도 모두 탐색
- 이 과정에서 프록시 객체를 만나면 초기화 발생

### 2. LAZY 설정 무시

- 프록시 객체는 실제 데이터에 대한 참조만 가짐
- 직렬화 시점에 실제 데이터가 필요
- 결과적으로 LAZY 설정이 무의미해짐

### 3. 연쇄적인 데이터 로딩

- 한 객체의 직렬화가 다른 객체의 직렬화를 유발
- 데이터베이스 호출이 기하급수적으로 증가

## 해결 방안

### 1. DTO 사용

```java
// DTO 클래스 정의
public class UserDTO {
    private Long id;
    private String name;

    public UserDTO(User user) {
        this.id = user.getId();
        this.name = user.getName();
    }
}

// 수정된 토큰 생성 메서드
public String generateToken(Long userId) {  // ID만 전달
    return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
            .compact();
}

// API 응답 수정
@GetMapping("/users/{id}")
public UserDTO getUser(@PathVariable Long id) {
    User user = userRepository.findById(id).orElseThrow();
    return new UserDTO(user);
}

```

### 2. JsonIgnore 사용

```java
@Entity
public class User {
    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Post> posts;
}

```

### 3. 캐시 키-값 최적화

```java
@Cacheable(value = "users", key = "#id")
public UserDTO getUserForCache(Long id) {  // DTO를 캐시
    User user = userRepository.findById(id).orElseThrow();
    return new UserDTO(user);
}

```

## 실무 적용 포인트

### 1. Entity vs DTO

- Entity는 영속성 계층 내에서만 사용
- 외부 통신이나 캐싱에는 항상 DTO 사용

### 2. 필요한 데이터만 조회

- JPQL이나 QueryDSL로 필요한 컬럼만 조회
- 불필요한 연관 관계 로딩 방지

### 3. 직렬화 범위 제한

- @JsonIgnore나 @JsonManagedReference 활용
- 양방향 관계에서 한쪽만 직렬화

## 주의할 점

### 1. 숨겨진 직렬화 발생 지점

- toString() 메서드
- 로깅 시점
- 디버거 사용 시
- equals(), hashCode() 메서드

### 2. 캐시 주의사항

- 캐시 데이터는 최소한으로 유지
- 연관 관계가 있는 엔티티는 별도 캐시
- 캐시 데이터 갱신 전략 수립
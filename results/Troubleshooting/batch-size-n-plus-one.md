# Batch Size를 통한 N+1 문제 해결

## Batch Size란?

Batch Size는 지연 로딩 시 IN 절을 사용하여 한 번에 여러 데이터를 조회하는 방식입니다.

### 동작 방식의 변화

Batch Size 미설정 시:

```sql
-- N+1 문제 발생
SELECT * FROM user WHERE id = 1;
SELECT * FROM user WHERE id = 2;
SELECT * FROM user WHERE id = 3;
...

```

Batch Size 설정 시:

```sql
-- 하나의 IN 쿼리로 해결
SELECT * FROM user WHERE id IN (1,2,3,...,N);

```

## 설정 방법

### 1. 글로벌 설정

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 1000

```

### 2. 엔티티별 설정

```java
@Entity
public class Post {
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "post")
    private List<Comment> comments;
}

```

### 3. 컬렉션별 설정

```java
@Entity
public class Order {
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order")
    private List<OrderItem> orderItems;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "order")
    private List<Payment> payments;
}

```

## 동작 원리

### 1. 프록시 초기화 시점의 동작

```java
List<Order> orders = orderRepository.findAll(); // 1번 쿼리

// Batch Size만큼 IN 절로 조회
orders.forEach(order -> {
    order.getOrderItems().size(); // IN 쿼리로 한 번에 조회
});

```

### 2. 최적화 원리

- 프록시 초기화 시점에 설정된 size만큼 한 번에 조회
- 연관 엔티티들을 미리 영속성 컨텍스트에 로딩
- 이후 프록시 초기화 시 추가 쿼리 없이 캐시에서 조회

## 실제 구현 예시

### 1. 주문 목록 조회

```java
@GetMapping("/api/v1/orders")
public List<OrderDto> getOrders() {
    List<Order> orders = orderRepository.findAll();
    return orders.stream()
        .map(order -> OrderDto.builder()
            .id(order.getId())
            .orderItems(order.getOrderItems()) // Batch Size 적용
            .payments(order.getPayments())     // Batch Size 적용
            .build())
        .collect(Collectors.toList());
}

```

### 2. 게시글 목록 조회

```java
@Entity
public class Post {
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "post")
    private List<Comment> comments;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "post")
    private List<Like> likes;
}

@GetMapping("/api/v1/posts")
public List<PostDto> getPosts() {
    return postRepository.findAll().stream()
        .map(post -> PostDto.builder()
            .id(post.getId())
            .commentCount(post.getComments().size())
            .likeCount(post.getLikes().size())
            .build())
        .collect(Collectors.toList());
}

```

## 성능 최적화 포인트

### 1. 적절한 Batch Size 설정

```java
// 너무 작은 size
@BatchSize(size = 10)  // 많은 쿼리 발생

// 너무 큰 size
@BatchSize(size = 10000)  // 메모리 부하 위험

// 적절한 size
@BatchSize(size = 1000)  // 보통 100~1000 사이 권장

```

### 2. 여러 연관관계 처리

```java
@Entity
public class Order {
    @ManyToOne(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    private User user;

    @OneToMany(mappedBy = "order")
    @BatchSize(size = 100)
    private List<OrderItem> orderItems;

    @OneToOne(fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    private Delivery delivery;
}

```

### 3. Fetch Join과의 조합

```java
@Query("SELECT o FROM Order o JOIN FETCH o.user")
List<Order> findAllWithUser();  // ToOne 관계는 Fetch Join

// ToMany 관계는 Batch Size 활용
@BatchSize(size = 100)
@OneToMany(mappedBy = "order")
private List<OrderItem> orderItems;

```

## 주의사항

### 1. 메모리 사용량

```java
// 메모리 사용량 모니터링
@Aspect
@Component
public class MemoryMonitorAspect {
    @Around("@annotation(MemoryCheck)")
    public Object checkMemory(ProceedingJoinPoint joinPoint) throws Throwable {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();

        Object result = joinPoint.proceed();

        long after = runtime.totalMemory() - runtime.freeMemory();
        log.info("Memory used: {} MB", (after - before) / 1024 / 1024);

        return result;
    }
}

```

### 2. 데이터베이스 부하

```java
// 쿼리 실행 시간 모니터링
@Aspect
@Component
public class QueryTimeAspect {
    @Around("@annotation(QueryTime)")
    public Object measureQueryTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object result = joinPoint.proceed();

        stopWatch.stop();
        log.info("Query execution time: {} ms", stopWatch.getTotalTimeMillis());

        return result;
    }
}

```

## 실무 활용 패턴

### 1. 페이징 처리와 함께 사용

```java
@GetMapping("/api/v2/posts")
public Page<PostDto> getPostsWithPaging(Pageable pageable) {
    return postRepository.findAll(pageable)
        .map(post -> PostDto.builder()
            .id(post.getId())
            .commentCount(post.getComments().size())  // Batch Size 적용
            .likeCount(post.getLikes().size())        // Batch Size 적용
            .build());
}

```

### 2. 복잡한 도메인 모델

```java
@Entity
public class Order {
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order")
    private List<OrderItem> orderItems;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order")
    private List<Payment> payments;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order")
    private List<DeliveryLog> deliveryLogs;
}

```

### 3. 캐시와 함께 사용

```java
@Cacheable(value = "orders", key = "#orderId")
public OrderDto getOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(EntityNotFoundException::new);

    // Batch Size가 적용된 연관 엔티티 로딩
    return OrderDto.builder()
        .id(order.getId())
        .items(order.getOrderItems())
        .payments(order.getPayments())
        .build();
}

```
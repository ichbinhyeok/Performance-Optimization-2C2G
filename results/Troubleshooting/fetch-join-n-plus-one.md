# Fetch Join을 통한 N+1 문제 해결

## Fetch Join이란?

Fetch Join은 SQL JOIN과는 다른 JPA만의 특별한 기능입니다. 일반적인 JOIN은 실제 데이터를 즉시 가져오지 않지만, Fetch Join은 연관된 엔티티를 즉시 함께 조회합니다.

### 일반 JOIN vs Fetch Join

일반 JOIN:

```java
// JPQL
@Query("SELECT p FROM Post p JOIN p.comments")
List<Post> findAllWithComments();

// 실행되는 SQL
SELECT p.*
FROM post p
JOIN comment c ON p.id = c.post_id;

```

Fetch Join:

```java
// JPQL
@Query("SELECT p FROM Post p JOIN FETCH p.comments")
List<Post> findAllWithComments();

// 실행되는 SQL
SELECT p.*, c.*
FROM post p
JOIN comment c ON p.id = c.post_id;

```

## 동작 원리

### 1. 일반 JOIN

- SELECT절에 연관 테이블의 컬럼이 포함되지 않음
- 연관 엔티티는 프록시 상태 유지
- 실제 데이터 접근 시점에 추가 쿼리 발생

### 2. Fetch JOIN

- SELECT절에 연관 테이블의 모든 컬럼을 포함
- 연관 엔티티도 영속성 컨텍스트에 함께 캐싱
- 추가 쿼리 없이 모든 데이터를 즉시 조회

## 구현 방법

### 1. 단일 Fetch Join

```java
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p JOIN FETCH p.user")
    List<Post> findAllWithUser();

    @Query("SELECT DISTINCT p FROM Post p JOIN FETCH p.comments")
    List<Post> findAllWithComments();
}

```

### 2. 다중 Fetch Join

```java
@Query("""
    SELECT DISTINCT p
    FROM Post p
    JOIN FETCH p.user
    JOIN FETCH p.comments
    WHERE p.id IN :ids
""")
List<Post> findPostsWithUserAndComments(@Param("ids") List<Long> ids);

```

### 3. 연관 엔티티의 연관 엔티티 조회

```java
@Query("""
    SELECT DISTINCT p
    FROM Post p
    JOIN FETCH p.comments c
    JOIN FETCH c.user
    WHERE p.id = :postId
""")
Optional<Post> findPostWithCommentsAndUsers(@Param("postId") Long postId);

```

## 주의사항

### 1. 페이징 쿼리와 함께 사용 시

```java
// 메모리에서 페이징 처리 (주의!)
@Query("SELECT p FROM Post p JOIN FETCH p.comments")
Page<Post> findAllWithComments(Pageable pageable);  // 잘못된 사용

// 대안: ToOne 관계만 Fetch Join + BatchSize
@Query("SELECT p FROM Post p JOIN FETCH p.user")
Page<Post> findAllWithUser(Pageable pageable);  // 올바른 사용

```

### 2. 카테시안 곱 주의

```java
// 데이터 중복 발생
@Query("""
    SELECT p
    FROM Post p
    JOIN FETCH p.comments
    JOIN FETCH p.likes
""")
List<Post> findAllWithCommentsAndLikes();  // 주의 필요

// 해결: DISTINCT 사용
@Query("""
    SELECT DISTINCT p
    FROM Post p
    JOIN FETCH p.comments
    JOIN FETCH p.likes
""")
List<Post> findAllWithCommentsAndLikes();

```

### 3. Collection Fetch Join 제한

```java
// 두 개의 컬렉션을 Fetch Join (불가능)
@Query("""
    SELECT p
    FROM Post p
    JOIN FETCH p.comments
    JOIN FETCH p.likes
""")
List<Post> findAllWithCommentsAndLikes();  // 에러 발생 가능

// 해결: 하나의 컬렉션만 Fetch Join + @BatchSize
@Query("SELECT p FROM Post p JOIN FETCH p.comments")
List<Post> findAllWithComments();

```

## 활용 패턴

### 1. ToOne 관계 우선 적용

```java
@Query("""
    SELECT p
    FROM Post p
    JOIN FETCH p.user
    JOIN FETCH p.category
""")
List<Post> findAllWithUserAndCategory();

```

### 2. BatchSize와 조합

```java
@Entity
public class Post {
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "post")
    private List<Comment> comments;
}

@Query("SELECT p FROM Post p JOIN FETCH p.user")
List<Post> findAllWithUser();

```

### 3. 상황별 쿼리 분리

```java
public interface PostRepository extends JpaRepository<Post, Long> {
    // 목록 조회용
    @Query("SELECT p FROM Post p JOIN FETCH p.user")
    List<Post> findAllWithUser();

    // 상세 조회용
    @Query("""
        SELECT p
        FROM Post p
        JOIN FETCH p.user
        JOIN FETCH p.comments c
        JOIN FETCH c.user
        WHERE p.id = :id
    """)
    Optional<Post> findByIdWithUserAndComments(@Param("id") Long id);
}

```

## 성능 최적화 팁

### 1. 조회 데이터 최소화

```java
@Query("""
    SELECT new com.example.dto.PostDTO(
        p.id,
        p.title,
        u.username,
        SIZE(p.comments)
    )
    FROM Post p
    JOIN p.user u
""")
List<PostDTO> findAllPostSummary();

```

### 2. 인덱스 활용

```sql
-- 인덱스 생성
CREATE INDEX idx_post_user ON posts (user_id);
CREATE INDEX idx_comment_post ON comments (post_id);

```

### 3. 실행 계획 확인

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

```

## 실무 적용 사례

### 1. API 응답 성능 최적화

```java
@GetMapping("/api/v1/posts")
public List<PostResponse> getPosts() {
    return postRepository.findAllWithUserAndCategory()
        .stream()
        .map(PostResponse::from)
        .collect(Collectors.toList());
}

```

### 2. 복잡한 도메인의 조회 최적화

```java
@Query("""
    SELECT DISTINCT o
    FROM Order o
    JOIN FETCH o.member
    JOIN FETCH o.delivery
    JOIN FETCH o.orderItems oi
    JOIN FETCH oi.item
    WHERE o.status = :status
""")
List<Order> findAllWithMemberDelivery(@Param("status") OrderStatus status);

```

### 3. 성능 모니터링

```java
@Aspect
@Component
public class QueryCountAspect {
    private final ThreadLocal<Long> queryCount = new ThreadLocal<>();

    @Around("@annotation(QueryCount)")
    public Object countQueries(ProceedingJoinPoint joinPoint) throws Throwable {
        queryCount.set(0L);
        Object result = joinPoint.proceed();
        log.info("Query count: {}", queryCount.get());
        return result;
    }
}

```
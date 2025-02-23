# JPA N+1 문제란?

JPA에서 연관 관계를 가진 엔티티를 조회할 때 발생하는 성능 문제입니다. 하나의 쿼리로 N개의 데이터를 가져온 후, 각 데이터의 연관 엔티티를 조회하기 위해 N번의 추가 쿼리가 발생하는 현상을 말합니다.

## N+1이 발생하는 근본적인 이유

1. **지연 로딩(Lazy Loading)의 특성**
    - JPA는 연관 엔티티를 실제로 사용할 때까지 로딩을 미룸
    - 실제 데이터 접근 시점에 개별 쿼리 실행
2. **프록시 객체의 초기화**
    - 지연 로딩된 엔티티는 프록시 객체로 생성
    - 프록시 객체 접근 시 실제 데이터를 로딩하기 위한 쿼리 발생

## 발생 상황 예시

```java
@Entity
public class Post {
    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    private List<Comment> comments;
}

// 실제 코드
List<Post> posts = postRepository.findAll(); // 1번 쿼리
for (Post post : posts) {
    System.out.println(post.getComments().size()); // N번의 추가 쿼리
}

```

실행되는 SQL:

```sql
-- 1번: 게시글 조회
SELECT * FROM post;

-- N번: 각 게시글의 댓글 조회
SELECT * FROM comment WHERE post_id = 1;
SELECT * FROM comment WHERE post_id = 2;
SELECT * FROM comment WHERE post_id = 3;
...

```

## 해결 방안 개요

### 1. 로직 개선

- 연관 관계 조회가 필요 없는 최적화된 쿼리 사용
- 필요한 데이터만 정확하게 조회하는 방식 채택

### 2. Fetch Join

- JOIN을 사용한 단일 쿼리로 연관 엔티티까지 완전히 조회
- 지연 로딩이 아닌 즉시 로딩 방식으로 동작

예시:

```java
@Query("SELECT p FROM Post p JOIN FETCH p.comments")
List<Post> findAllWithComments();

```

### 3. Batch Size

- IN절을 활용한 일괄 데이터 조회로 쿼리 수 감소
- 지연 로딩의 장점은 유지하면서 성능 최적화

설정 예시:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 1000

```

### 4. 재귀 쿼리

- 계층 구조 데이터를 단일 쿼리로 조회
- CTE를 활용한 효율적인 조회

## 각 해결방안의 특징

1. **로직 개선**
    - 장점: 문제의 근본적 해결
    - 단점: 상황별 다른 접근 필요
2. **Fetch Join**
    - 장점: 즉시 모든 데이터 조회 가능
    - 단점: 메모리 사용량 증가 가능
3. **Batch Size**
    - 장점: 유연한 지연 로딩 유지
    - 단점: 최적 크기 설정 필요
4. **재귀 쿼리**
    - 장점: 계층 구조에 최적화
    - 단점: 복잡한 구현

## 해결 방안 선택 기준

상황에 따른 최적의 해결 방안:

1. **단순 조회**
    - 연관 데이터가 불필요할 때 → 로직 개선
    - 연관 데이터도 필요할 때 → Fetch Join
2. **대량 데이터**
    - 페이징 필요 시 → Batch Size
    - 전체 데이터 필요 시 → Fetch Join
3. **계층 구조**
    - 고정 깊이 → Fetch Join
    - 가변 깊이 → 재귀 쿼리
# 🚀 JPA N+1 문제의 이해와 해결 방안

## N+1 문제란?

JPA에서 연관 관계를 가진 엔티티를 조회할 때 발생하는 성능 문제입니다. 하나의 쿼리로 N개의 데이터를 가져온 후, 각 데이터의 연관 엔티티를 조회하기 위해 N번의 추가 쿼리가 발생하는 현상을 말합니다.

### N+1이 발생하는 근본적인 이유

1. **지연 로딩(Lazy Loading)의 특성**
    - JPA는 연관 엔티티를 실제로 사용할 때까지 로딩을 미룸
    - 실제 데이터 접근 시점에 개별 쿼리 실행
2. **프록시 객체의 초기화**
    - 지연 로딩된 엔티티는 프록시 객체로 생성
    - 프록시 객체 접근 시 실제 데이터를 로딩하기 위한 쿼리 발생

## 해결 방안 개요

JPA N+1 문제를 해결하기 위한 4가지 주요 접근 방식이 있습니다:

1. **로직 개선**: N+1이 발생할 수 있는 상황 자체를 제거
2. **Fetch Join**: 연관 엔티티를 한 번의 쿼리로 함께 조회
3. **Batch Size**: IN 절을 활용한 일괄 조회로 쿼리 수 최적화
4. **재귀 쿼리**: 계층 구조 데이터를 단일 쿼리로 조회

## 핵심 인사이트

각 해결 방안은 다음과 같은 특징적인 장점을 가지고 있습니다:

- **로직 개선**: 문제의 근본적 해결이 가능하며, 가장 효율적인 방법
- **Fetch Join**: 즉시 로딩이 필요한 경우 최적의 선택
- **Batch Size**: 유연한 지연 로딩과 성능 최적화의 균형
- **재귀 쿼리**: 계층 구조에서 뛰어난 성능 발휘

## 해결 방안 상세 설명

### 1. 로직 개선을 통한 해결

### 핵심 원리

- 연관 관계 조회가 필요 없는 최적화된 쿼리 사용
- 필요한 데이터만 정확하게 조회하는 방식 채택
- 비즈니스 로직 레벨에서의 최적화

### 구체적 구현

```java
// 개선 전: N+1 발생
Post post = postRepository.findById(postId);
PostLike postLike = post.getLikes().stream()
    .filter(like -> like.getUser().getId().equals(userId))
    .findFirst()
    .orElseThrow();

// 개선 후: 단일 쿼리로 해결
@Transactional
public void unlikePost(Long postId, Long userId) {
    PostLike postLike = postLikeRepository.findByPostIdAndUserId(postId, userId)
            .orElseThrow();
    postLikeRepository.delete(postLike);
}

```

### 2. Fetch Join을 통한 해결

### 왜 Fetch Join으로 N+1이 해결되는가?

Fetch Join은 SQL JOIN과는 다른 JPA만의 특별한 기능입니다. 일반적인 JOIN은 실제 데이터를 즉시 가져오지 않지만, Fetch Join은 연관된 엔티티를 즉시 함께 조회합니다.

1. **작동 원리**
    - 일반 JOIN: SELECT 절에 연관 테이블의 컬럼이 포함되지 않음
    - Fetch JOIN: SELECT 절에 연관 테이블의 모든 컬럼을 포함
    - 연관 엔티티도 영속성 컨텍스트에 함께 캐싱
2. **실제 생성되는 SQL의 차이**

```sql
-- 일반 JOIN
SELECT p.* FROM post p JOIN user u ON p.user_id = u.id

-- Fetch JOIN
SELECT p.*, u.* FROM post p JOIN user u ON p.user_id = u.id

```

### 핵심 원리

- JOIN을 사용한 단일 쿼리로 연관 엔티티까지 완전히 조회
- 지연 로딩이 아닌 즉시 로딩 방식으로 동작
- 영속성 컨텍스트에 연관 엔티티까지 모두 캐싱

### 주의사항

- 페이징 쿼리 사용 시 메모리 문제 발생 가능
- 두 개 이상의 컬렉션을 Fetch Join 시 카테시안 곱 주의
- OneToMany 관계에서 중복 데이터 발생 가능

### 구체적 구현

```java
@Query("""
    SELECT DISTINCT p FROM Post p
    LEFT JOIN FETCH p.user
    LEFT JOIN FETCH p.likes
    WHERE p.id IN :ids
""")
List<Post> findPostsWithUserAndLikes(@Param("ids") List<Long> ids);

```

### 3. Batch Size 설정을 통한 해결

### 왜 Batch Size로 N+1이 해결되는가?

Batch Size는 설정된 크기만큼 IN 절을 사용하여 한 번에 여러 데이터를 조회하는 방식입니다.

1. **동작 방식의 변화**

```sql
-- Batch Size 미설정 시 (N+1 발생)
SELECT * FROM user WHERE id = 1;
SELECT * FROM user WHERE id = 2;
SELECT * FROM user WHERE id = 3;
...

-- Batch Size 설정 시 (하나의 IN 쿼리로 해결)
SELECT * FROM user WHERE id IN (1,2,3,...,N);

```

1. **최적화 원리**
    - 프록시 초기화 시점에 설정된 size만큼 한 번에 조회
    - 연관 엔티티들을 미리 영속성 컨텍스트에 로딩
    - 이후 프록시 초기화 시 추가 쿼리 없이 캐시에서 조회

### 핵심 원리

- IN 절을 활용한 일괄 데이터 조회로 쿼리 수 감소
- 지연 로딩의 장점은 유지하면서 성능 최적화
- 여러 컬렉션 조회시에도 효과적

### 실무 적용 포인트

- 보통 100~1000 사이의 값을 설정
- 너무 크면 메모리 사용량 증가, 너무 작으면 쿼리 수 증가
- OneToMany 관계에서 특히 효과적

### 구체적 구현

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 1000

```

### 4. 재귀 쿼리를 통한 해결

### 왜 재귀 쿼리로 N+1이 해결되는가?

재귀 쿼리(Recursive CTE)는 자기 참조 관계를 가진 데이터를 단일 쿼리로 조회하는 SQL 표준 문법입니다.

1. **전통적인 방식의 문제점**

```sql
-- 각 계층마다 별도 쿼리 실행 (N+1 발생)
SELECT * FROM comment WHERE parent_id = 1;        -- 1번 쿼리
SELECT * FROM comment WHERE parent_id IN (2,3);   -- 2번 쿼리
SELECT * FROM comment WHERE parent_id IN (4,5,6); -- 3번 쿼리

```

1. **재귀 쿼리의 동작 원리**
    - Anchor Member: 초기 데이터 집합 선택
    - Recursive Member: 이전 결과를 기반으로 다음 계층 조회
    - Union All: 모든 계층의 결과를 합침

### 핵심 원리

- CTE를 활용한 계층 구조의 일괄 조회
- 단일 쿼리로 전체 계층 데이터를 한 번에 조회
- DB 엔진 레벨에서의 최적화 수행

### 실무 적용 시 고려사항

- 무한 재귀 방지를 위한 제한 설정 필요
- 데이터베이스 벤더별 문법 차이 존재
- 복잡한 정렬이나 필터링이 필요한 경우 주의

### 구체적 구현

```java
    @Query(value = """
    WITH RECURSIVE CommentHierarchy AS (
        -- 초기 선택: 첫 번째 레벨의 자식들
        SELECT
            c.id,
            c.content,
            c.created_date,
            c.modified_date,
            c.depth,
            c.parent_comment_id,
            c.post_id,
            c.user_id,
            1 as hierarchy_depth,
            CAST(c.id AS CHAR(255)) AS path
        FROM SNS.comments c
        WHERE c.parent_comment_id = :parentId

        UNION ALL

        -- 재귀 부분: 각 레벨의 자식들을 연속해서 선택
        SELECT
            c.id,
            c.content,
            c.created_date,
            c.modified_date,
            c.depth,
            c.parent_comment_id,
            c.post_id,
            c.user_id,
            h.hierarchy_depth + 1,
            CONCAT(h.path, ',', c.id)
        FROM SNS.comments c
        INNER JOIN CommentHierarchy h ON c.parent_comment_id = h.id
        WHERE h.hierarchy_depth < 10
    )
    SELECT
        c.*,
        u.id as author_id,
        u.username as author_name,
        u.email as author_email
    FROM CommentHierarchy c
    JOIN SNS.users u ON c.user_id = u.id
    ORDER BY c.path
""", nativeQuery = true)
    List<Comment> findAllChildrenHierarchy(@Param("parentId") Long parentId);

```

## 해결 방안 선택 가이드

각 상황에 따른 최적의 해결 방안:

1. **단일 데이터 조회 시**: 로직 개선
2. **연관 데이터 즉시 필요 시**: Fetch Join
3. **유연한 지연 로딩 필요 시**: Batch Size
4. **계층형 데이터 조회 시**: 재귀 쿼리

## 심화 인사이트

### 성능과 트레이드오프

1. **메모리 vs 쿼리 수**
    - Fetch Join: 한 번에 많은 데이터를 메모리에 로드
    - Batch Size: 적절한 크기로 분할하여 메모리 관리
2. **즉시성 vs 유연성**
    - Fetch Join: 즉시 로딩으로 빠른 조회
    - Batch Size: 필요한 시점에 최적화된 조회
3. **단순성 vs 확장성**
    - 로직 개선: 간단하지만 확장이 제한적
    - 재귀 쿼리: 복잡하지만 높은 확장성

## 결론

N+1 문제는 단순한 기술적 이슈가 아닌 설계와 구현의 균형이 필요한 문제입니다. 각 해결 방안의 특성을 이해하고 상황에 맞는 최적의 방법을 선택하는 것이 중요합니다.

특히 주의할 점은:

- 하나의 방법만으로는 모든 상황을 해결할 수 없음
- 비즈니스 요구사항과 기술적 제약을 모두 고려해야 함
- 지속적인 모니터링과 개선이 필요한 영역임
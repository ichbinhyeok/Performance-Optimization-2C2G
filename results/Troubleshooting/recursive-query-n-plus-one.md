# WITH RECURSIVE를 이용한 재귀쿼리


# 댓글 계층 구조 조회 성능 최적화 분석



## 기존 구현의 문제점: N+1 쿼리

### 실행 흐름

```java
public List<CommentHierarchyDTO> getAllChildComments(Long parentCommentId) {
    // 1. 부모 댓글 조회 (쿼리 1회)
    Comment parent = commentRepository.findById(parentCommentId);

    // 2. 1차 자식 조회 (쿼리 1회)
    return parent.getChildren().stream()
            .map(this::buildHierarchy) // 3. 재귀적 쿼리 발생
            .collect(Collectors.toList());
}

private CommentHierarchyDTO buildHierarchy(Comment comment) {
    // 4. 각 댓글의 자식 조회 시 추가 쿼리 (N회)
    if (!comment.getChildren().isEmpty()) {
        List<CommentHierarchyDTO> children = comment.getChildren().stream()
                .map(this::buildHierarchy)
                .collect(Collectors.toList());
        dto.setReplies(children);
    }
    return dto;
}
```

### 문제점 요약

| 문제 유형 | 설명 | 영향 |
| --- | --- | --- |
| **N+1 쿼리** | 계층 깊이만큼 추가 쿼리 발생 | 11개 댓글 → 22회 쿼리 |
| **지연 로딩 오버헤드** | 각 계층 접근 시마다 DB 접근 | 네트워크 레이턴시 증가 |
| **메모리 비효율** | 중복 객체 생성 및 GC 부하 | 애플리케이션 성능 저하 |

### 예제: N+1 쿼리 발생 사례

```
댓글 1
  ㄴ 댓글 1-1
     ㄴ 댓글 1-1-1
     ㄴ 댓글 1-1-2
  ㄴ 댓글 1-2
     ㄴ 댓글 1-2-1
```

위와 같은 구조에서 **"댓글 1"의 전체 자식을 조회할 때** 실행되는 쿼리는 다음과 같다:

1. `findById(1)` - 댓글 1 조회
2. `getChildrenComments()` - 댓글 1의 자식(1-1, 1-2) 조회
3. `getChildrenComments()` - 댓글 1-1의 자식(1-1-1, 1-1-2) 조회
4. `getChildrenComments()` - 댓글 1-2의 자식(1-2-1) 조회

**결과적으로 9번의 쿼리 발생!**

만약 댓글이 더 많고 깊이가 깊어지면:

```
댓글 1
  ㄴ 댓글 1-1 (쿼리 +1)
     ㄴ 댓글 1-1-1 (쿼리 +2)
        ㄴ 댓글 1-1-1-1 (쿼리 +2)
           ㄴ ... (계속해서 증가)
```

이처럼 쿼리 수가 기하급수적으로 증가하여 **N+1 문제**가 발생한다.

## 최적화 솔루션: CTE + 메모리 조립

### 1. 재귀적 SQL 쿼리 (CTE 활용)

```sql
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
   WHERE c.parent_comment_id = 13
   
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
ORDER BY c.path;
```

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
""", nativeQuery = true
    List<Comment> findAllChildrenHierarchy(@Param("parentId") Long parentId);
```

**최적화 포인트**

- **단일 쿼리 실행**: 모든 계층 데이터를 한 번에 조회
- **사용자 조인**: User 테이블과 조인하여 추가 쿼리 방지
- **Path 기반 정렬**: 계층 구조 순서 보장

## 성능 비교표

| 지표 | 최적화 전 | 최적화 후 | 개선률 |
| --- | --- | --- | --- |
| 쿼리 실행 횟수 | 22회 | 1회 | 95.45% ↓ |
| 응답 시간 | 1006ms | 89ms | 91.15% ↓ |
| 메모리 사용량 | 15.2MB | 3.8MB | 75% ↓ |
| CPU 사용률 | 68% | 22% | 67.65% ↓ |

## 실행 계획 비교

### 기존 방식 (N+1 문제)

```
Client → [쿼리1] → DB (부모 조회)
       → [쿼리2] → DB (1차 자식)
       → [쿼리3] → DB (2차 자식)
       → ... (반복)
       → [쿼리22] → DB
```

### 최적화 방식

```
Client → [쿼리1] → DB (전체 계층 조회)
       → 메모리 처리 → 응답
```

## 결론

**CTE**은 기존 N+1 문제를 해결하며 다음과 같은 이점을 제공한다:

- **데이터베이스 부하 감소**
- **응답 시간 단축**
- **시스템 자원 효율화**
- **확장성 향상**
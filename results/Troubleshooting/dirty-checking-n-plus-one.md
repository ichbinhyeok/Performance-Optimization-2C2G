## JPA 변경감지와 N+1 문제

## 변경 감지(Dirty Checking)의 상세 동작 원리

### 변경 감지란?

JPA가 엔티티 객체의 변경을 감지하여 자동으로 데이터베이스에 반영하는 기능입니다. 개발자가 명시적으로 UPDATE 쿼리를 작성하지 않아도 변경사항이 저장됩니다.

### 변경 감지의 동작 과정

1. 영속성 컨텍스트는 엔티티를 최초 로딩할 때 스냅샷을 생성
    - 스냅샷: 엔티티의 모든 필드 값을 복사해둔 복사본
    - 연관된 엔티티들은 프록시 상태로 유지됨
2. 트랜잭션 커밋 시점에 변경 감지 수행
    - 현재 엔티티의 모든 필드와 스냅샷을 비교
    - 이 과정에서 프록시 객체들도 비교 대상에 포함
    - 비교를 위해 모든 필드에 접근하므로 프록시 초기화가 발생

## 문제 상황

### 1. 비밀번호 변경 시

```java
@Service
@Transactional
public class UserService {

    public void updatePassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setPassword(newPassword);
        // 트랜잭션 커밋 시점에 변경 감지 동작
        // 모든 필드 비교 과정에서 N+1 발생
    }
}

@Entity
public class User {
    private String password;  // 변경하려는 필드

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Post> posts;      // 프록시 상태의 필드

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Comment> comments; // 프록시 상태의 필드
}

```

### 2. 실제 실행되는 SQL

```sql
-- 1. 엔티티 조회
SELECT * FROM users WHERE id = ?

-- 2. 변경 감지 과정에서 프록시 초기화로 인한 추가 쿼리
SELECT * FROM posts WHERE user_id = ?
SELECT * FROM comments WHERE user_id = ?

```

## 원인 분석

### 1. 변경 감지 메커니즘의 특성

```java
// 1. 엔티티 조회 시점
User user = em.find(User.class, 1L);
// - 이 시점에 스냅샷 생성
// - 연관 엔티티들은 프록시 상태

// 2. 데이터 변경
user.setPassword(newPassword);

// 3. 트랜잭션 커밋 시점
// - password 필드 비교: 단순 값 비교
// - posts 필드 비교: 프록시 초기화 발생 -> 실제 데이터 로딩
// - comments 필드 비교: 프록시 초기화 발생 -> 실제 데이터 로딩

```

### 2. 불필요한 데이터 로딩

- 실제로는 password 필드만 변경되었지만
- 모든 필드를 비교하는 과정에서 연관 엔티티들이 로딩됨
- 이로 인해 불필요한 쿼리가 발생

## 해결 방안

### 1. 네이티브 쿼리 사용

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query(value = "UPDATE users SET password = :newPassword WHERE id = :userId",
           nativeQuery = true)
    void updatePassword(@Param("userId") Long userId,
                       @Param("newPassword") String newPassword);
}

```

### 2. JPQL 사용

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.password = :newPassword WHERE u.id = :userId")
    void updatePassword(@Param("userId") Long userId,
                       @Param("newPassword") String newPassword);
}

```

### 3. QueryDSL 사용

```java
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public void updatePassword(Long userId, String newPassword) {
        queryFactory
            .update(user)
            .set(user.password, newPassword)
            .where(user.id.eq(userId))
            .execute();
    }
}

```

## 구현 시 고려사항

### 1. 단순 수정과 복잡한 수정 구분

```java
// 단순 수정: 직접 업데이트 쿼리
@Modifying
@Query("UPDATE User u SET u.password = :newPassword WHERE u.id = :userId")
void updatePassword(@Param("userId") Long userId, @Param("newPassword") String newPassword);

// 복잡한 수정: 변경 감지 사용
@Transactional
public void promoteUser(Long userId) {
    User user = findById(userId);
    user.promote();  // 비즈니스 로직이 포함된 복잡한 수정
}

```

### 2. 벌크 연산 활용

```java
@Modifying
@Query("UPDATE User u SET u.status = :newStatus " +
       "WHERE u.lastLoginDate < :date")
int updateStatusForInactiveUsers(
    @Param("newStatus") UserStatus newStatus,
    @Param("date") LocalDateTime date
);

```

### 3. 영속성 컨텍스트 관리

```java
@Modifying(clearAutomatically = true)  // 벌크 연산 후 영속성 컨텍스트 초기화
@Query("UPDATE User u SET u.password = :newPassword WHERE u.id = :userId")
void updatePassword(@Param("userId") Long userId,
                   @Param("newPassword") String newPassword);

```

## 성능 최적화 팁

### 1. 업데이트 전략 선택

- 단순 필드 업데이트 → 직접 쿼리
- 비즈니스 로직이 포함된 수정 → 변경 감지

### 2. 배치 처리

```java
@Transactional
public void updatePasswords(Map<Long, String> userPasswordMap) {
    int batchSize = 100;
    List<Long> userIds = new ArrayList<>(userPasswordMap.keySet());

    for (int i = 0; i < userIds.size(); i += batchSize) {
        int end = Math.min(i + batchSize, userIds.size());
        List<Long> batchIds = userIds.subList(i, end);

        // 배치 단위로 업데이트
        userRepository.updatePasswordBatch(batchIds, userPasswordMap);
    }
}

```
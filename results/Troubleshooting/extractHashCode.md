# JPA Set 컬렉션과 extractHashCode 이해하기

## 1. extractHashCode 병목 현상의 발견

```
org.hibernate.type.descriptor.java.AbstractClassJavaType.extractHashCode - 45,730 ms (4.7%)

```

### 1.1 왜 발생하는가?

```java
@Entity
public class Comment {
    @OneToMany(mappedBy = "parentComment")
    private Set<Comment> childrenComments = new HashSet<>();
}

```

1. 서버 시작 시 모든 엔티티 스캔
2. Set 컬렉션 필드 발견
3. HashSet 초기화 과정에서 extractHashCode 호출
4. 이 과정이 엔티티마다, 필드마다 반복

## 2. extractHashCode의 내부 동작

### 2.1 일반 객체의 hashCode vs Hibernate의 extractHashCode

```java
// 일반 객체의 hashCode
public int hashCode() {
    return System.identityHashCode(this); // 단순 메모리 주소 기반
}

// Hibernate의 extractHashCode
protected int extractHashCode(Object value) {
    if (value == null) return 0;

    // 1. 프록시 객체 확인
    if (value instanceof HibernateProxy) {
        // 프록시 초기화 여부 확인
        // 실제 엔티티 추출
    }

    // 2. 영속성 상태 확인
    if (value instanceof PersistentCollection) {
        // 컬렉션 초기화 상태 확인
    }

    // 3. 실제 hashCode 계산
    return value.hashCode();
}

```

## 3. 성능에 미치는 영향

### 3.1 서버 시작 시

```
1. 모든 엔티티 클래스 로딩
2. Set 필드마다 초기화
3. 각 초기화마다 extractHashCode 호출
↓
서버 시작 시간 증가 (전체의 4.7%)

```

### 3.2 런타임에서

```java
@Transactional
public void addChildComment(Comment parent, Comment child) {
    parent.getChildrenComments().add(child);
    // 여기서도 extractHashCode 호출됨
}

```

## 4. 왜 이렇게 복잡한 과정이 필요한가?

### 4.1 JPA의 특성 때문

1. **프록시 객체 지원**

    ```java
    Comment proxy = entityManager.getReference(Comment.class, 1L);
    // 실제 객체가 아닌 프록시
    
    ```

2. **지연 로딩**

    ```java
    Comment comment = repository.findById(1L).get();
    Set<Comment> children = comment.getChildrenComments();
    // 아직 초기화되지 않은 컬렉션
    
    ```

3. **영속성 컨텍스트 관리**

    ```java
    // 서로 다른 트랜잭션에서 가져온 같은 ID의 엔티티
    Comment c1 = tx1.findById(1L);
    Comment c2 = tx2.findById(1L);
    // 다른 인스턴스지만 같은 엔티티로 취급해야 함
    
    ```


## 5. 이해의 포인트

### 5.1 Set + JPA의 결합이 가져오는 복잡성

1. **Set의 특성**
    - **중복 방지**: Set은 중복된 값을 허용하지 않는다. 이를 위해 내부적으로 `hashCode`와 `equals`를 사용해 중복 여부를 판단한다.
    - **해시 기반 구조**: `HashSet`은 객체의 `hashCode`를 기반으로 저장 위치를 결정하므로, 해시코드 계산이 빈번하게 발생한다.
2. **JPA의 특성**
    - **프록시 객체**
    - **지연 로딩**
    - **영속성 컨텍스트**
3. **두 특성의 결합**
    - 단순 hashCode로는 처리 불가
    - `extractHashCode`라는 복잡한 과정 필요

### 5.2 실제 의미

```java
// 이런 단순한 코드 한 줄이
Set<Comment> comments = new HashSet<>();

// 내부적으로는 이런 복잡한 과정을 거침
1. 프록시 객체 확인
2. 영속성 상태 확인
3. 컬렉션 초기화 상태 확인
4. 실제 해시코드 계산

```

## 6. 추가: Set의 기본 개념

### 6.1 Set이란?

- **정의**:

  Set은 **중복된 요소를 허용하지 않는 컬렉션**이다. 저장되는 모든 요소는 유일해야 하며, 이 특성을 보장하기 위해 `hashCode`와 `equals` 메서드를 사용한다.

- **주요 특징**:
    - **중복 방지**: 같은 객체(또는 동일한 hashCode와 equals 결과를 가진 객체)가 여러 번 추가되지 않는다.
    - **순서 미보장**: 대부분의 Set 구현체(예: HashSet)는 저장 순서를 보장하지 않는다. (순서가 중요한 경우 LinkedHashSet이나 TreeSet을 사용할 수 있다.)
    - **빠른 검색**: 내부적으로 해시 테이블을 사용해 객체를 저장하고 검색하기 때문에 검색 속도가 빠르다.

### 6.2 왜 hashCode가 중요한가?

- **hashCode의 역할**:
  Set은 객체를 저장할 때 `hashCode`를 기준으로 객체를 분류하고, 이후 동일한 hashCode를 가진 객체에 대해 `equals` 메서드를 사용해 실제 중복 여부를 확인한다.
- **Hibernate와의 충돌**:
  JPA 환경에서는 프록시 객체, 지연 로딩, 그리고 영속성 컨텍스트 관리로 인해 단순한 hashCode 계산 방식이 아닌, 복잡한 로직(`extractHashCode`)이 필요하다. 이로 인해 Set을 초기화하거나 요소를 추가할 때마다 추가적인 비용이 발생하게 된다.


## 해결방안
// 대안 1: List 사용 (중복 허용하되 비즈니스 로직에서 처리)
    
    @OneToMany(mappedBy = "parentComment")
    private List<Comment> childrenComments = new ArrayList<>();

// 대안 2: Set 사용 시 equals/hashCode 최적화

    @Entity
    public class Comment {
    @Id
    private Long id;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Comment)) return false;
            Comment comment = (Comment) o;
            return id != null && id.equals(comment.getId());
        }
        
        @Override
        public int hashCode() {
            return getClass().hashCode();
        }
    }


## 7. 결론

이 병목 현상은 단순한 성능 이슈가 아니라, JPA가 제공하는 다양한 기능(프록시, 지연 로딩 등)을 안전하게 지원하기 위해 필요한 필수적인 과정임을 보여준다.

서버 시작 시 4.7%의 시간이 소요된다는 수치는 이러한 복잡한 매커니즘의 비용을 명확하게 나타낸다.

또한, **Set** 컬렉션의 사용이 단순한 자료구조 선택 이상의 의미를 가지며, JPA와 함께 사용할 때 발생할 수 있는 내부 동작 및 성능 이슈를 이해하는 것이 중요하다.
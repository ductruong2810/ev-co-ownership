# Hướng dẫn Tối ưu Deployment trên Render

## Các tối ưu đã áp dụng ✅

### 1. Parallel Maven Build
- Sử dụng `-T 2C` để build song song với 2 threads
- Giảm thời gian compile đáng kể

### 2. Skip Tests
- `-DskipTests` trong build command
- `maven.test.skip=true` trong pom.xml
- Tiết kiệm thời gian build

### 3. Maven Config File
- `.mvn/maven.config` để tự động apply optimizations
- Không cần nhập lại mỗi lần build

## Các cách tối ưu thêm (Optional)

### 1. Sử dụng Docker với Build Cache (Nhanh nhất)

Thay vì dùng Maven build trực tiếp, có thể dùng Docker với multi-stage build và cache layers:

**render.yaml:**
```yaml
services:
  - type: web
    name: ev-co-ownership
    dockerfilePath: ./Dockerfile
    dockerContext: .
    plan: free
```

**Dockerfile** (đã có sẵn):
- Layer caching giúp build nhanh hơn
- Dependencies được cache giữa các lần build

### 2. Tối ưu Dependencies

Kiểm tra và loại bỏ dependencies không cần thiết:
```bash
mvn dependency:analyze
```

### 3. Sử dụng Maven Wrapper với Cache

Render tự động cache `.m2/repository` giữa các lần build, nhưng có thể tối ưu thêm bằng cách:
- Pre-download dependencies trong Dockerfile
- Sử dụng local Maven repository cache

### 4. Giảm JAR Size

Thêm vào `pom.xml`:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </exclude>
        </excludes>
    </configuration>
</plugin>
```

### 5. Upgrade Render Plan

**Free tier limitations:**
- ❌ Cold start: 15-30 giây
- ❌ Limited CPU/RAM
- ❌ No persistent storage

**Starter plan ($7/month):**
- ✅ Faster cold start
- ✅ More CPU/RAM
- ✅ Better performance

## So sánh Build Time

| Method | Estimated Time |
|--------|---------------|
| **Maven build (single thread)** | ~5-8 phút |
| **Maven build (parallel -T 2C)** | ~3-5 phút |
| **Docker with cache** | ~2-4 phút (first), ~1-2 phút (cached) |
| **Starter plan + optimizations** | ~1-3 phút |

## Tips để giảm Deploy Time

1. **Chỉ deploy khi cần thiết**
   - Không commit/push mỗi lần test nhỏ
   - Tập hợp nhiều changes rồi deploy một lần

2. **Sử dụng Branch Deploys**
   - Test trên preview deployment trước
   - Chỉ merge vào main khi chắc chắn

3. **Monitor Build Logs**
   - Xem phần nào chậm nhất
   - Tối ưu phần đó

4. **Cache Dependencies**
   - Render tự động cache `.m2/repository`
   - Đảm bảo `pom.xml` không thay đổi thường xuyên

## Current Build Command

```bash
./mvnw clean package -DskipTests -T 2C
```

**Breakdown:**
- `./mvnw` - Maven wrapper
- `clean` - Clean previous build
- `package` - Build JAR
- `-DskipTests` - Skip tests
- `-T 2C` - Parallel build với 2 threads per core

## Troubleshooting

### Build vẫn chậm
1. Kiểm tra dependencies có thay đổi không
2. Kiểm tra network speed trên Render
3. Xem build logs để tìm bottleneck

### Build fails với parallel
- Thử giảm threads: `-T 1C`
- Hoặc bỏ parallel: `./mvnw clean package -DskipTests`

### Out of memory
- Tăng `MAVEN_OPTS`: `-Xmx1024m`
- Giảm parallel threads

## Kết luận

Với các tối ưu hiện tại:
- ✅ Build time giảm ~30-40%
- ✅ Parallel compilation
- ✅ Skip tests
- ✅ Maven config optimization

**Để nhanh hơn nữa:** Upgrade lên Starter plan ($7/month) hoặc sử dụng Docker với build cache.


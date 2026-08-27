# RDS PostgreSQL

이 상세 SOT는 staging application이 사용할 RDS for PostgreSQL의 배치·credential·storage·network·
monitoring·backup mental model과 현재 선택·대안·재검토 조건을 소유합니다. DB subnet group과
DB instance를 실제로 만드는 절차는
[RDS 최초 구축 runbook](../../operations/140-rds-provisioning-runbook.md)이 소유합니다.

## 역할과 배치

RDS DB instance 한 대가 managed PostgreSQL을 제공합니다. 두 private subnet을 포함한 DB
subnet group은 RDS가 network interface를 배치할 수 있는 **기존 subnet 후보 목록**입니다.
Subnet을 새로 만드는 resource가 아닙니다.

```text
VPC 10.0.0.0/16
├── EC2 Kubernetes node: public subnet 2a
└── RDS DB subnet group
    ├── private subnet 2a: 10.0.128.0/20
    └── private subnet 2b: 10.0.144.0/20
```

DB는 public IP를 받지 않고 같은 VPC의 EC2 security group에서만 PostgreSQL `5432`를
허용합니다. 현재 Single-AZ writer 한 대라 instance·AZ 장애 자동 standby failover는
제공하지 않습니다. 두 subnet 후보가 있다는 사실만으로 DB가 Multi-AZ가 되지는 않습니다.

## Engine·생성 방식·가용성

| 옵션 | 현재 선택 | 판정 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- | --- |
| Engine | PostgreSQL | Application 계약과 일치 | Spring datasource driver·URL과 Flyway migration이 PostgreSQL을 대상으로 합니다. | Database engine 변경 |
| 생성 방식 | 전체 구성 | 재현성 목표에 부합 | 가용성·storage·network·monitoring·backup·maintenance를 직접 확인·고정합니다. | 세부값보다 임시 생성 속도가 우선 |
| Template | 개발/테스트 | Staging 목적에 적합 | Production preset 비용을 피하되 세부값은 template에 맡기지 않고 명시합니다. | Production 승격·일회성 sandbox 분리 |
| 배포 | Single-AZ DB instance 한 대 | 비용 우선 조건부 선택 | Standby 비용을 쓰지 않지만 DB·AZ 장애 자동 failover가 없습니다. | HA·복구 목표 확정 |
| Multi-AZ cluster version filter | 비활성 | 현재 배포와 일치 | Multi-AZ DB cluster 후보만 볼 필요가 없습니다. | Multi-AZ DB cluster 선택 |
| Engine version | `PostgreSQL 16.14-R2` | 당시 최종 Console 선택 | PostgreSQL 16 line을 사용하지만 exact patch를 영구 최신으로 보지 않습니다. | 생성 시 제공 version 변경·지원 종료 |
| Extended Support | 비활성 | Standard support 범위 | 지원 종료 뒤 구 major를 유료 유지하지 않고 종료 전에 upgrade할 책임을 둡니다. | Standard support 안에 major upgrade 불가 |

### Engine·template·가용성 대안

| 대안 | 의미 | 현재 제외 이유 | 적합해지는 조건 |
| --- | --- | --- | --- |
| Aurora MySQL | Aurora 분산 storage와 MySQL 호환 endpoint | PostgreSQL driver·migration과 다릅니다. | MySQL 전환과 Aurora 특성 요구 |
| Aurora PostgreSQL | PostgreSQL 호환 Aurora cluster | 작은 instance 한 대보다 비용·운영 범위가 큽니다. | Reader·storage·failover 특성이 필요 |
| MySQL·MariaDB | MySQL 계열 RDS | PostgreSQL 전용 계약과 다릅니다. | Application DB 계약 변경 |
| Oracle·SQL Server·Db2 | 상용·별도 engine과 license model | 고유 기능·license 요구가 없고 계약·비용이 다릅니다. | 해당 고유 기능·license 요구 |
| 손쉬운 생성 | AWS preset이 세부값 일부를 숨깁니다. | 모든 option과 선택 이유를 재현해야 합니다. | 폐기 가능한 빠른 임시 생성 |
| Production template | HA·빠른 failover preset | Staging 비용 목표보다 큽니다. | Production SLA·비용 승인 |
| Sandbox template | 새 기능·일회성 실험 preset | 실제 staging application 연결 대상으로 사용합니다. | 별도 폐기 가능한 실험 DB |
| Multi-AZ DB cluster | Writer와 두 reader를 3 AZ에 둡니다. | Instance 세 대 비용과 3-AZ subnet 요구가 현재 범위보다 큽니다. | Read scaling·짧은 failover 요구 |
| Multi-AZ DB instance | Primary와 synchronous standby를 2 AZ에 둡니다. | Standby 비용보다 staging 비용 절감을 우선합니다. | DB·AZ 자동 failover 요구 |
| Extended Support 활성 | Standard 종료 뒤 구 major를 유료 지원 | PostgreSQL 16을 standard support 안에서 upgrade할 계획입니다. | 종료 전 upgrade 불가 |

## 식별자·credential·인증

| 옵션 | 현재 선택 | 판정 | 이유와 영향 |
| --- | --- | --- | --- |
| DB identifier | `stg-car-community-postgres` | 환경·project·역할 식별 | Account·region 안의 resource identifier이며 `Name` tag도 맞춥니다. |
| Master user | `carmunity_admin` | 초기 관리 account | Schema·user 생성에 사용하며 application runtime 최소 권한 account가 아닙니다. |
| Credential 관리 | RDS가 AWS Secrets Manager에서 관리 | Secret 저장 기본기에 부합 | RDS가 master password와 secret lifecycle을 함께 관리하고 문서·Git·수동 메모에 값을 남기지 않습니다. |
| Secret KMS key | `(default) aws/secretsmanager` | 현재 key 요구에 적합 | Customer key policy·cross-account 요구 없이 AWS managed key를 씁니다. 생성 뒤 이 managed secret의 key는 바꿀 수 없습니다. |
| DB authentication | Password | 현재 application 입력과 일치 | JDBC가 username·password를 사용하되 master와 application role을 분리합니다. |

Secrets Manager 관리가 password가 요청마다 바뀐다는 뜻은 아닙니다. RDS가 관리하는 master
secret은 기본 7일 rotation schedule을 가지며, rotation 때 secret version과 DB master
password가 함께 바뀝니다. Master credential은 application에 주입하지 않고 최초 application
role 생성과 승인된 복구에만 사용합니다.

Application은 local `.env`에서 서로 다른 runtime·migrator password를 bootstrap 입력으로
받습니다. 같은 값을 PostgreSQL role과 Kubernetes Secret에 각각 적용하며 `.env` file 전체나
master password를 EC2에 저장하지 않습니다. Secrets Manager 선택이 application Secret을
자동으로 만들거나 주입하지는 않습니다.

자체 관리 password는 생성·보관·rotation 책임을 사람에게 돌려 제외합니다. Customer managed
secret key는 별도 key policy가 없어 제외합니다. Password+IAM DB 인증은 현재 EC2 role이
ECR pull only이고 application token 생성·갱신이 없으며 환경변수 계약과 다릅니다.
Password+Kerberos는 연결할 directory·domain identity가 없습니다.

### Application role 분리

| Role | 사용 주체 | 권한 |
| --- | --- | --- |
| `carmunity_runtime` | 정상 application Pod 전체 | Database 접속과 `public` schema 사용. Migration 성공 뒤 application table DML과 identity sequence 사용 권한을 받습니다. |
| `carmunity_migrator` | 단일 migration Job | Database 접속과 `public` schema의 `USAGE`, `CREATE` |

두 role은 staging RDS 최초 설정 때 한 번 생성합니다. Runtime에는 schema `CREATE`와 role 관리
권한을 주지 않습니다. Migration Job의 Flyway `afterMigrate` callback이 현재 table·sequence에
runtime 최소 권한을 부여하므로 Pod 수가 늘어나도 migration을 반복 실행하지 않습니다.

Role 생성은 `100-provision-database-access.sh`가 소유합니다. 이 script는 staging DB URL과
role 이름을 고정하고 세 password만 NUL-delimited 입력으로 받아 PostgreSQL 16 client가
없으면 설치하고,
고정 이름의 두 role 중 없는 role만 생성한 뒤 위 최소 권한을 적용합니다. 기존 role과
password는 유지하며, 재실행할 때도
idempotent한 권한문을 다시 적용합니다. 완료 확인은 두 role의 존재까지만 수행합니다.
Role 속성·membership·ownership·기존 password와 전체 권한을 감사하거나 자동 교정하는
범용 운영 도구는 아닙니다.

## Instance와 storage

| 옵션 | 현재 선택 | 판정 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- | --- |
| 이전 세대 class | 후보에서 제외 | 새 구성에 적합 | Legacy class를 섞지 않습니다. | Legacy compatibility 필수 |
| Class category | Burstable T | 작은 staging에 적합 | Baseline·credit model로 고정 비용을 낮추며 지속 고CPU에는 제한될 수 있습니다. | Credit 고갈·지속 부하 |
| Type | `db.t4g.micro`, 2 vCPU·1 GiB | 비용 우선 조건부 선택 | Graviton2 PostgreSQL이며 network endpoint이므로 app host architecture와 직접 결합하지 않습니다. Memory·connection 여유는 작습니다. | Memory pressure·connection 부족·CPU credit 제약 |
| Storage | Gp3 | 작은 범용 DB에 적합 | 용량과 기본 성능을 분리한 general-purpose SSD입니다. | 측정 latency·IOPS 증가 |
| 할당 용량 | `20 GiB` | Console 최소 시작 | 큰 미사용 용량을 먼저 사지 않습니다. | Data growth가 근접 |
| IOPS | 포함 `3000`, 입력 비활성 | 400 GiB 미만 gp3 baseline | 추가 IOPS를 구매하지 않습니다. | 400 GiB 이상·실측 IOPS 병목 |
| Throughput | 포함 `125 MiB/s`, 입력 비활성 | 400 GiB 미만 gp3 baseline | 추가 처리량을 구매하지 않습니다. | 실측 throughput 병목 |
| Storage autoscaling | 활성 | Storage-full 위험 완화 | Downtime 없이 늘릴 수 있지만 자동 축소하지 못합니다. | 자동 증가 금지 비용 통제 |
| Autoscaling 상한 | `100 GiB` | 비용 상한 조건부 선택 | 예상 밖 무제한 성장 비용을 막습니다. 늘어난 용량은 축소할 수 없습니다. | 정상 growth가 100 GiB 접근 |

Standard class·memory optimized는 실측 resource 요구 없이 상시 비용을 늘려 제외합니다.
`db.t3.micro`는 x86이고 지원되는 더 새로운 Graviton T4g를 선택했지만 x86-only extension이
있으면 재검토합니다. Io1·io2 Provisioned IOPS와 magnetic·다른 storage는 측정된 SLA가
없습니다. Autoscaling 비활성은 storage-full write 중단 위험을 늘리고, Console의 1,000 GiB
기본 제안은 작은 staging의 예상 밖 비용 폭을 키워 제외합니다.

## DB subnet group

| 항목 | 현재 선택 | 이유와 영향 |
| --- | --- | --- |
| 이름 | `stg-car-community-rds-subnet-group` | 환경·project·RDS 용도를 식별하며 생성 뒤 이름을 바꿀 수 없습니다. |
| VPC | `stg-car-community-vpc` | EC2와 같은 VPC의 private routing·SG 참조를 사용합니다. |
| AZ | `ap-northeast-2a`, `ap-northeast-2b` | RDS가 요구하는 두 AZ 후보와 실제 VPC subnet을 일치시킵니다. |
| Subnet 1 | Private 2a `10.0.128.0/20` | 외부에 직접 route하지 않는 placement 후보입니다. |
| Subnet 2 | Private 2b `10.0.144.0/20` | 두 번째 AZ의 placement 후보입니다. |

Console 목록에 `2c`·`2d` AZ가 보여도 현재 VPC에 해당 AZ subnet이 없으면 실제 group에
추가할 수 없습니다. AZ 이름만 고르면 subnet·CIDR이 생기지 않습니다. `2c`·`2d`를 쓰려면
VPC에 subnet·route를 먼저 설계·생성해야 합니다.

RDS의 자동 설정은 기존 적합 group을 재사용하거나 없으면 여러 AZ의 private subnet·route와
group을 만들 수 있습니다. 이미 두 private subnet을 명시적으로 만들었으므로 예상하지 않은
network resource 추가를 막기 위해 기존 group을 사용합니다. Public subnet 두 개는 기술적으로
후보가 될 수 있지만 private-only 배치와 다릅니다. 세 번째 private subnet은 Multi-AZ DB
cluster용 3-AZ를 선택할 때 필요합니다.

## EC2 연결·network·security

| 옵션 | 현재 선택 | 판정 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- | --- |
| Compute 연결 | EC2 resource에 연결 | 현재 consumer에 적합 | Console이 기존 node와 private SG 관계를 구성합니다. | 다른 compute·여러 consumer·수동 SG 정책 |
| EC2 | `stg-car-community-k8s-node-01` | 현재 single node | DB consumer를 명시하지만 EC2 status·SSH 성공 증거는 아닙니다. | Consumer 변경 |
| Network type | IPv4 | VPC 계약과 일치 | IPv6 CIDR이 없고 EC2 자동 연결에서는 dual-stack을 쓸 수 없습니다. | VPC dual-stack 전환 |
| VPC | `stg-car-community-vpc` | EC2에서 파생 | 같은 VPC의 private address·SG reference를 쓰며 생성 뒤 변경할 수 없습니다. | 새 DB로 migration |
| DB subnet group | 위 기존 group | 명시적 network 재사용 | Console의 새 subnet 자동 생성을 허용하지 않습니다. | Existing group 미사용 결정 |
| Public access | 아니요 | DB 비공개 기본기에 부합 | Public IP를 주지 않고 VPC path와 허용 SG consumer만 접근합니다. | 예외 외부 client·source·TLS 승인 |
| DB SG | 새 `stg-car-community-rds-sg` | 이름 있는 DB 경계 | DB 전용 group이며 public ingress를 추가하지 않습니다. | Canonical shared DB SG 확정 |
| 자동 SG pair | RDS 쪽 `rds-ec2-N`, EC2 쪽 `ec2-rds-N` | Source-to-destination 최소 rule | RDS inbound는 EC2 SG source `5432`, EC2 outbound는 RDS SG destination을 사용합니다. 숫자는 account 상태에 따라 달라 고정 이름이 아닙니다. | 여러 consumer·수동 policy |
| AZ | `ap-northeast-2a` | Single-AZ·EC2 연결 파생 | EC2와 같은 AZ로 현재 path를 단순화하고 group에는 2b 후보를 남깁니다. | Multi-AZ·placement 변경 |
| RDS Proxy | 없음 | 현재 규모에 적합 | 별도 시간·용량 비용 없이 application connection pool이 endpoint에 직접 연결합니다. | Connection 폭증·too many connections·Lambda/Auto Scaling |
| Server CA | `rds-ca-rsa2048-g1` 기본 + Seoul bundle | AWS server CA와 client 검증 | Runtime·migration 모두 `sslmode=verify-full`과 RDS endpoint hostname을 사용합니다. Public CA bundle은 별도 ConfigMap으로 mount합니다. | CA rotation 요구 |
| Port | `5432` | PostgreSQL·app 계약 | Local JDBC 기본 port와 SG 허용 범위를 맞춥니다. | 조직 network가 5432 차단 |

연결 안 함은 network·SG를 전부 수동 소유해야 해 현재 정확한 consumer가 있는 조건에서
제외합니다. Public access `예`는 internet exposure를 만들고, 기존 SG 재사용은 canonical
DB group이 아직 없으며, custom port는 application·SG 복잡도만 늘립니다. 다른 CA는 client
trust·rotation 요구가 있을 때, RDS Proxy는 connection churn·failover 개선이 비용보다
필요해질 때 도입합니다.

## Tag·monitoring·log

DB tag는 `Name=stg-car-community-postgres`, `project=car-community`, `environment=stg`,
`managed-by=manual`입니다. 검색·비용·현재 관리 방식을 표현하고 resource 동작을 바꾸지
않습니다.

| 옵션 | 현재 선택 | 판정 | 이유와 영향 |
| --- | --- | --- | --- |
| Database Insights | Standard | 현재 staging 관측에 적합 | DB load·query 기본 지표를 보고 Advanced fleet 분석·장기 보관 비용을 피합니다. |
| 상세 DB·query metric | 활성 | 문제 분석에 유용 | Query 원인 분석 surface를 유지합니다. |
| 보존 | 7일 무료 | 비용 우선 시작 | 단기 장애·성능 분석을 지원하고 유료 장기 trend를 두지 않습니다. |
| Insights KMS | `(default) aws/rds` | 현재 key 요구에 적합 | AWS managed key를 사용하며 생성 뒤 이 key를 바꿀 수 없다는 경계를 수용합니다. |
| Enhanced Monitoring | 활성 | 사용자 관측성 선호 | Host OS process·CPU·memory를 CloudWatch Logs로 보내며 소액 비용보다 진단을 우선합니다. |
| OS metric granularity | 60초 | 비용·관측 균형 | 더 촘촘한 log 양 없이 1분 상태를 봅니다. |
| Monitoring role | 기본값 | Console 기본 관리 경로 | 별도 표준 role 없이 Console의 기본 role 생성·재사용을 사용합니다. |
| IAM DB auth error log | 비활성 | 인증 방식과 일치 | IAM DB 인증을 사용하지 않아 해당 source가 없습니다. |
| PostgreSQL log | Export 활성 | 중앙 진단 | Error·connection·statement 진단을 CloudWatch Logs에서 합니다. Log volume·보존 비용은 별도입니다. |
| Upgrade log | Export 활성 | Upgrade 진단 | Engine version 변경 정보를 중앙에 남깁니다. |
| Log IAM role | RDS service-linked role | Service 경계와 일치 | `AWSServiceRoleForRDS`이며 Enhanced Monitoring OS role과 다릅니다. |
| DevOps Guru | 비활성 | 현재 규모에 불필요 | 기존 Insights·Enhanced·log로 시작합니다. |

Advanced Insights, 유료 1~24개월 보존과 customer KMS는 fleet·장기 trend·key policy 요구가
없어 제외합니다. 상세 metric 비활성은 작은 비용보다 query 분석을 잃습니다. Enhanced
Monitoring 비활성은 사용자가 소액 비용을 수용해 제외했고 1·5·10·15·30초는 현재 높은
해상도 사례가 없습니다. 별도 monitoring role은 표준 role이 없고, IAM auth log는 인증
기능 자체가 꺼져 있으며, PostgreSQL·upgrade log 미수집은 중앙 진단을 잃습니다. DevOps
Guru는 instance 한 대에 추가 service 범위를 넣지 않습니다.

## Database·암호화·backup·maintenance

| 옵션 | 현재 선택 | 판정 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- | --- |
| Initial DB | `carmunity` | Application 계약과 일치 | 기본 JDBC database와 맞추며 비우면 application DB를 자동 생성하지 않습니다. | DB 이름 변경 |
| Parameter group | `default.postgres16` | Custom 요구 없는 시작 | TLS 강제·logging·connection limit custom 값은 미결정입니다. | Custom parameter·rollback 확정 |
| Option group | `default:postgres-16` | 추가 option 없음 | PostgreSQL 16 기본 option만 사용합니다. | 지원 option 요구 |
| Storage encryption | 활성 | At-rest 기본기에 부합 | Storage·log·backup·replica·snapshot을 암호화하며 생성 뒤 unencrypted로 전환하지 않습니다. | 다른 key 정책은 새 DB migration 때 |
| Storage KMS | `(default) aws/rds` | 현재 key lifecycle에 적합 | AWS managed key를 사용하고 생성 뒤 DB key를 바꿀 수 없음을 수용합니다. | Customer key·cross-account backup 요구 |
| Automated backup | 활성 | 복구 기본기에 부합 | Snapshot·transaction log로 보존 기간 안의 point-in-time recovery를 제공합니다. | 다른 backup 정책이 대체 |
| Retention | 7일 | Staging 복구·비용 균형 | 일주일 PITR이며 장기 감사·DR 보존은 아닙니다. | 더 긴 복구 목표 |
| Backup window | AWS 기본 | Traffic 미확정 | AWS 지정 UTC window를 쓰고 production traffic이 생기면 maintenance와 겹치지 않게 고정합니다. | Traffic 시간대 확정 |
| Automated backup tag 복사 | 활성 | 추적 일관성 | 생성 시 tag를 한 번 복사하며 이후 DB tag 수정이 자동 동기화되지는 않습니다. | Backup 별도 taxonomy |
| Snapshot tag 복사 | 활성 | Metadata 일관성 | Snapshot access·비용·검색 분류를 유지합니다. | Snapshot 별도 taxonomy |
| Cross-region backup | 비활성 | Staging 비용 우선 | DR storage·transfer·KMS·복구 운영을 추가하지 않습니다. | Cross-region RTO/RPO 확정 |
| Minor auto-upgrade | 활성 | Security·bugfix 적용 | AWS가 검토한 backward-compatible minor를 maintenance window에 적용하며 짧은 중단을 수용합니다. | 모든 patch 수동 승인 필요 |
| Maintenance window | AWS 기본 | Traffic 미확정 | 임의 시간을 고정하지 않으며 production에서 저부하 변경 window를 정합니다. | Traffic·변경 window 확정 |
| Deletion protection | 활성 | 실수 삭제 방지 | 삭제 전에 명시적으로 해제해야 하며 backup을 대신하지 않습니다. | 승인된 폐기 시에만 일시 해제 |

Initial DB 비움, custom parameter·option group은 현재 application 계약·검증·rollback이 없어
제외합니다. Encryption 비활성과 automated backup 비활성은 복구·보안 기본기를 잃습니다.
Customer KMS는 별도 key lifecycle이 없습니다. Retention 1~6일은 발견 여유가 짧고 8~35일은
장기 보존 요구 없이 비용을 늘립니다. Backup·maintenance window 직접 지정은 traffic이
미확정입니다. Tag 복사 비활성은 추적 누락을 만들고 cross-region은 DR 목표가 없습니다.
Minor auto-upgrade 비활성은 security·bugfix 적용 누락을 늘리며 deletion protection 비활성은
의도적 두 단계를 없앱니다.

## 당시 비용 estimate의 provenance

RDS 최종 Console 화면에서 확인한 **그 시점의** 월별 estimate는 DB instance `18.25 USD`,
storage `2.62 USD`, 합계 `20.87 USD`였습니다. 이는 당시 화면의 On-Demand 사용량 가정이며
현재 가격이나 고정 견적이 아닙니다. Backup storage, 해당 I/O·data transfer가 포함되지 않을
수 있고 Secrets Manager, CloudWatch Logs·Enhanced Monitoring도 각 service 사용량에 따라
별도 청구될 수 있습니다. 현재 비용 판단에는 AWS Pricing Calculator와 실제 billing을 다시
확인해야 합니다.

## 현재 경계와 재검토 trigger

- Runtime·migrator role과 Kubernetes Secret을 분리하고 master credential은 최초 role 생성과
  승인된 복구에만 사용합니다. Application password rotation은 최초 live 적용 뒤 별도 운영
  절차로 보완해야 합니다.
- JDBC는 `verify-full`과 Seoul RDS CA bundle을 사용합니다. CA rotation 때 bootstrap checksum과
  Kubernetes ConfigMap을 함께 갱신해야 합니다.
- RDS가 `사용 가능`이고 EC2 private DNS·TCP `5432`·현재 Secrets Manager credential 인증이
  성공해야 live 완료입니다. 현재 환경의 완료·미완료 판정은
  [Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)만
  소유합니다.
- Multi-AZ·Proxy·larger class·storage tuning은 장애 목표와 실측 부하를 근거로 도입합니다.
- DB deletion, backup restore와 disaster recovery의 실행 procedure는 별도 운영 설계가
  필요합니다.

Capacity·가용성·복구 후보의 상태와 도입 순서는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 근거와 관련 문서

- **실제 최초 구축 절차**: [RDS 최초 구축 runbook](../../operations/140-rds-provisioning-runbook.md)
- **Network 기반**: [110 VPC network](110-vpc-network.md)
- **Application host**: [120 EC2 Kubernetes node](120-ec2-kubernetes-node.md)
- **Application 환경변수 계약**: [Local development](../../development/100-local-development.md),
  [`application.yaml`](../../../src/main/resources/application.yaml)
- [RDS DB instance 생성](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_CreateDBInstance.html)
- [RDS PostgreSQL 생성·연결](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_GettingStarted.CreatingConnecting.PostgreSQL.html)
- [RDS DB subnet group](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html)
- [RDS instance class](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.DBInstanceClass.Types.html)
- [RDS storage](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_Storage.html)
- [Storage autoscaling](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIOPS.Autoscaling.html)
- [RDS Secrets Manager credential](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html)
- [RDS encryption](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.Encryption.html)
- [RDS automated backup](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html)
- [RDS resource tag](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_Tagging.html)
- [RDS monitoring·log](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.LoggingAndMonitoring.html)
- [CloudWatch Database Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Database-Insights.html)
- [RDS PostgreSQL log](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_LogAccess.Concepts.PostgreSQL.html)
- [RDS Proxy](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-proxy.html)
- [RDS PostgreSQL minor upgrade](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_UpgradeDBInstance.PostgreSQL.html)
- [RDS deletion protection](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_DeleteInstance.html)

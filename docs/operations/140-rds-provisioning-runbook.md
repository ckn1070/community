# RDS PostgreSQL 최초 구축 runbook

AWS Console에서 EC2 Kubernetes node가 사용할 private RDS for PostgreSQL DB instance를
처음 생성하고 network·credential·관측·backup 설정을 확인하는 절차입니다. Database
구조, Secrets Manager rotation, 가용성·비용 선택과 대안은
[RDS PostgreSQL](../project/infrastructure/130-rds-postgresql.md)이 소유합니다.

## 사용 시점과 actor

- **사용 시점**: VPC와 `stg-car-community-k8s-node-01`이 준비된 뒤 staging database를
  처음 만들 때 사용합니다.
- **actor**: RDS, Secrets Manager, security group·subnet group과 monitoring role을
  생성할 권한이 있는 AWS Console 운영자입니다.
- **실행 위치**: 서울 리전의 `Aurora and RDS` Console입니다.

## 전제와 입력

- region은 `ap-northeast-2`입니다.
- VPC `stg-car-community-vpc`에 2a·2b private subnet이 하나씩 존재합니다.
- EC2 `stg-car-community-k8s-node-01`이 같은 VPC에 있습니다.
- master credential 값은 AWS Secrets Manager가 생성·관리합니다. secret 값을 문서,
  Git, Console 캡처나 채팅에 복사하지 않습니다.
- master user는 초기 관리용입니다. Application runtime용 최소 권한 user나 secret 주입
  방식을 이 절차가 임의로 확정하지 않습니다.

## 정상 실행 순서와 단계별 기대 결과

### 1. DB subnet group 생성

DB subnet group은 새 subnet을 만드는 resource가 아니라 RDS가 network interface를 배치할
수 있는 **기존 subnet 목록**입니다. `Aurora and RDS → Subnet groups → Create DB subnet
group`에서 다음 값을 입력합니다.

| 옵션 | 입력값 |
| --- | --- |
| 이름 | `stg-car-community-rds-subnet-group` |
| VPC | `stg-car-community-vpc` |
| AZ | `ap-northeast-2a`, `ap-northeast-2b` |
| subnet 1 | `stg-car-community-subnet-private1-ap-northeast-2a`, `10.0.128.0/20` |
| subnet 2 | `stg-car-community-subnet-private2-ap-northeast-2b`, `10.0.144.0/20` |

선택 목록에 `ap-northeast-2c`·`2d`가 보여도 해당 VPC에 실제 subnet이 없으면 추가할 수
없습니다. AZ 이름 선택만으로 subnet이나 CIDR이 만들어지지 않습니다. Public subnet을
섞지 않고 서로 다른 두 AZ의 private subnet 두 개만 선택합니다. 생성 뒤 subnet group
이름은 수정할 수 없습니다.

### 2. Engine, 생성 방식과 가용성

`Aurora and RDS → 데이터베이스 → 데이터베이스 생성`에서 다음 값을 입력합니다.

| Console 옵션 | 입력값 |
| --- | --- |
| engine 유형 | `PostgreSQL` |
| database 생성 방식 | `전체 구성` |
| template | `개발/테스트` |
| 배포 옵션 | `단일 AZ DB 인스턴스 배포(인스턴스 1개)` |
| Multi-AZ DB cluster 지원 version filter | 비활성 |
| engine version | `PostgreSQL 16.14-R2` |
| RDS Extended Support | 비활성 |

Exact patch release가 Console에서 제공되지 않으면 다른 major·minor version을 추측해
선택하지 않고 지원 상태와 application 호환성 결정을 먼저 갱신합니다. `손쉬운 생성`,
Aurora, 다른 engine, production·sandbox template와 Multi-AZ 배포로 바꾸지 않습니다.

### 3. 식별자, credential과 인증

| Console 옵션 | 입력값 |
| --- | --- |
| DB instance 식별자 | `stg-car-community-postgres` |
| master 사용자 이름 | `carmunity_admin` |
| credential 관리 | `AWS Secrets Manager에서 관리` |
| secret KMS key | `(기본값) aws/secretsmanager` |
| database 인증 옵션 | `암호 인증` |

Secrets Manager 관리 credential은 password를 요청마다 바꾸는 방식이 아닙니다. 현재
계약에서는 RDS가 master secret과 DB password를 함께 관리하며 기본 7일 rotation
schedule을 가질 수 있습니다. 생성 후 실제 schedule을 확인합니다. Application이 현재
secret version을 다시 읽는 actor·주기·cache 방식은 별도 배포 계약이므로 master 값을
환경변수에 복사해 고정하지 않습니다. 자체 관리 password, IAM DB 인증과 Kerberos
인증을 선택하지 않습니다.

### 4. Instance와 storage

| Console 옵션 | 입력값 |
| --- | --- |
| 이전 세대 class 포함 | 비활성 |
| class category | `버스터블 클래스(t 클래스 포함)` |
| instance type | `db.t4g.micro` |
| storage type | `범용 SSD(gp3)` |
| 할당 storage | `20 GiB` |
| provisioned IOPS | `3000 IOPS`, 비활성 입력의 포함값 |
| storage throughput | `125 MiB/s`, 비활성 입력의 포함값 |
| storage 자동 조정 | 활성 |
| 최대 storage 임계값 | `100 GiB` |

자동 확장된 storage는 다시 축소되지 않습니다. 400 GiB 미만 화면에서 IOPS·throughput은
포함된 baseline으로 표시되며 별도 추가 성능을 입력하지 않습니다. `io1`·`io2`, 이전
세대, standard·memory optimized class와 `1000 GiB` 기본 제안으로 바꾸지 않습니다.

### 5. EC2 연결, network와 security group

| Console 옵션 | 입력값 |
| --- | --- |
| compute resource | `EC2 컴퓨팅 리소스에 연결` |
| EC2 instance | `stg-car-community-k8s-node-01` |
| network type | `IPv4` |
| VPC | `stg-car-community-vpc` (EC2 선택에서 파생) |
| DB subnet group 방식 | `기존 항목 선택` |
| DB subnet group | `stg-car-community-rds-subnet-group` |
| public access | `아니요` |
| VPC security group | `새로 생성` |
| 새 VPC security group 이름 | `stg-car-community-rds-sg` |
| AZ | `ap-northeast-2a` |
| RDS Proxy | 생성하지 않음 |
| 인증 기관 | `rds-ca-rsa2048-g1 (기본값)` |
| database port | `5432` |

RDS 생성 화면의 DB subnet group `자동 설정`을 사용하지 않습니다. 자동 설정은 조건에
따라 기존 group을 재사용하거나 새 private subnet·route table과 group을 만들 수 있으므로
이미 만든 두 private subnet의 explicit group을 선택합니다. 생성 뒤 VPC는 바꿀 수
없습니다.

EC2 자동 연결은 RDS 쪽 `rds-ec2-N`, EC2 쪽 `ec2-rds-N` security group pair를 만들 수
있습니다. `N`은 account 상태에 따라 달라지므로 `1`을 고정 이름으로 사용하지 않습니다.
현재 입력 화면의 preview가 `rds-ec2-1`, `ec2-rds-1`을 제안했더라도 생성된 live 이름을
다시 확인합니다.
RDS inbound는 EC2 security group source의 PostgreSQL `5432`, EC2 outbound는 RDS
security group destination인지 확인합니다. `stg-car-community-rds-sg`나 자동 group에
public inbound를 추가하지 않습니다.

CA 선택만으로 client TLS 검증이 강제되는 것은 아닙니다. Application bootstrap이 Seoul RDS
CA bundle을 고정 checksum으로 ConfigMap에 적용하고 runtime·migration JDBC URL은
`sslmode=verify-full`로 endpoint hostname까지 검증합니다. RDS Proxy는 현재 생성하지 않습니다.

### 6. Tag, monitoring과 log

Tag를 다음처럼 입력합니다.

| 키 | 값 |
| --- | --- |
| `Name` | `stg-car-community-postgres` |
| `project` | `car-community` |
| `environment` | `stg` |
| `managed-by` | `manual` |

Monitoring은 다음 값을 선택합니다.

| Console 옵션 | 입력값 |
| --- | --- |
| Database Insights mode | `Database Insights - 표준` |
| 세부 database·query 지표 수집 | 활성 |
| 보존 기간 | `7일(무료)` |
| Database Insights KMS key | `(default) aws/rds` |
| Enhanced Monitoring | 활성 |
| OS 지표 세부 수준 | `60초` |
| OS 지표 monitoring role | `기본값` |
| IAM DB auth error log | 비활성 |
| PostgreSQL log export | 활성 |
| upgrade log export | 활성 |
| log export IAM role | `RDS 서비스 연결 역할`, 비활성 입력 |
| DevOps Guru for RDS | 비활성 |

Enhanced Monitoring 기본 role과 RDS service-linked role은 서로 다른 책임입니다. 별도
custom role을 만들지 않습니다. Console은 Enhanced Monitoring 기본 role을 생성하거나
기존 기본 role을 재사용하고, log export에는 `AWSServiceRoleForRDS`를 사용합니다.
Database Insights KMS key는 DB를 만든 뒤 변경할 수 없다는 Console 경고를 확인합니다.
Advanced Insights, 유료 장기 보존, 더 짧은 monitoring granularity와 DevOps Guru를
선택하지 않습니다.

### 7. Database option, encryption, backup과 유지 관리

| Console 옵션 | 입력값 |
| --- | --- |
| 초기 database 이름 | `carmunity` |
| DB parameter group | `default.postgres16` |
| option group | `default:postgres-16` |
| storage 암호화 | 활성 |
| storage KMS key | `(default) aws/rds` |
| 자동 backup | 활성 |
| backup 보존 기간 | `7일` |
| backup window | `기본 설정 없음` |
| 자동 backup으로 tag 복사 | 활성 |
| snapshot으로 tag 복사 | 활성 |
| 다른 AWS Region에 backup 복제 | 비활성 |
| minor version 자동 upgrade | 활성 |
| maintenance window | `기본 설정 없음` |
| 삭제 방지 | 활성 |

초기 database 이름을 비우지 않습니다. 생성 뒤 storage 암호화 KMS key는 바꿀 수 없고,
삭제 방지를 해제하기 전에는 DB를 삭제할 수 없습니다. Backup·maintenance window는 현재
traffic pattern이 확정되지 않아 AWS가 지정하도록 두며 production traffic이 생기면
재검토합니다. 자동 backup tag 복사는 생성 시점 tag의 일회 복사이므로 이후 DB tag
변경을 자동 동기화한다고 가정하지 않습니다.

### 8. 현재 예상 요금 확인과 생성

`데이터베이스 생성`을 누르기 전에 Console이 현재 region·시점·On-Demand 가정으로
표시하는 월별 estimate와 화면이 안내하는 별도 과금 가능성을 다시 확인합니다. 과거
화면의 point-in-time estimate와 포함·미포함 범위는
[RDS PostgreSQL 개념](../project/infrastructure/130-rds-postgresql.md#당시-비용-estimate의-provenance)이
소유하며 현재 수용값으로 사용하지 않습니다.

모든 입력이 일치하면 `데이터베이스 생성`을 선택합니다.

### 9. 생성 직후 단계 기대 결과

DB instance가 `사용 가능`이 된 뒤 다음을 확인합니다.

- 식별자, engine·version·class·storage가 입력표와 같습니다.
- endpoint는 private network에서 해석되고 public access가 `아니요`입니다.
- DB subnet group에는 2a·2b private subnet만 있습니다.
- 자동 연결 security group pair가 생성되고 `5432`가 EC2 security group source에서만
  허용됩니다.
- Secrets Manager에 RDS 관리 master secret이 생성됐습니다. 값을 출력·복사하지
  않습니다.
- encryption, automated backup, monitoring·log export와 deletion protection이
  입력표와 같습니다.

최종 연결 checkpoint는 EC2에서 private DNS, TCP `5432`, 현재 Secrets Manager credential
인증으로 `carmunity` database에 접속하는 것입니다. 안전한 secret 조회·주입 실행 방식이
아직 별도 배포 절차로 확정되지 않았다면 값을 임의로 shell history나 파일에 복사하지
않고 이 checkpoint를 **미완료**로 남깁니다. Master user를 장기 application credential로
고정하지 않습니다.

## 중단·실패 판정

- VPC·DB subnet group·EC2가 서로 다른 VPC이면 생성하지 않습니다.
- DB subnet group에 public subnet이 섞이거나 서로 다른 2개 AZ를 충족하지 못하면
  생성하지 않습니다.
- public access가 `예`, storage encryption·automated backup·deletion protection이
  비활성이면 생성하지 않습니다.
- 예상하지 않은 subnet·route table·public security group rule이 생성되면 완료로
  처리하지 않습니다.
- RDS가 `사용 가능`이 아니거나 EC2에서 DNS·TCP `5432`·credential 인증 중 하나가
  실패하면 application 연결 단계로 진행하지 않습니다.

## 복구·재개

1. `Events`와 DB instance 상태에서 생성 실패 원인을 확인합니다.
2. Network 오류면 VPC, DB subnet group의 두 private subnet과 자동 security group
   source·destination을 입력표에 다시 대조합니다.
3. Credential 오류면 현재 Secrets Manager secret version과 rotation 상태를 확인하되
   값을 문서나 shell history에 남기지 않습니다.
4. 잘못 생성된 DB를 바로 삭제하지 않습니다. 삭제 방지, automated backup·snapshot과
   KMS key의 lifecycle을 확인하고 승인된 폐기·교체 절차가 있을 때만 변경합니다.
5. 마지막 정상 checkpoint부터 재개하고, 실제 인증을 완료하지 못했으면 프로젝트 상태를
   `RDS 생성 완료`로 확대하지 않습니다.

## 완료 확인

- [ ] `데이터베이스 생성`이 성공하고 DB instance가 `사용 가능`입니다.
- [ ] engine·version·class·storage·network·tag·monitoring·backup·encryption·삭제 방지가
  입력표와 같습니다.
- [ ] DB subnet group과 security group이 private-only 연결 계약을 충족합니다.
- [ ] EC2에서 private DNS와 TCP `5432` 연결이 성공합니다.
- [ ] 현재 Secrets Manager credential로 `carmunity` database 인증이 성공합니다.
- [ ] 실제 완료·미완료 상태를
      [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에
      반영했습니다.

## 공식 기준과 관련 문서

- **선택·영향·대안**: [RDS PostgreSQL](../project/infrastructure/130-rds-postgresql.md)
- **상위 절차**: [AWS 최초 구축](100-aws-initial-provisioning-runbook.md)

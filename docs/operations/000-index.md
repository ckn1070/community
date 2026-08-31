# 운영 문서 목차

이 그룹은 실제 AWS·EC2·Kubernetes·GitHub·Argo CD 환경에서 사람이 수행하는 구축,
반복 점검·배포와 변경·복구 절차를 소유합니다. 시스템의 구성요소·작동 원리·선택 이유는
[100 현재 infrastructure overview](../project/infrastructure/100-infrastructure-overview.md)와
[Infrastructure 개념 목차](../project/infrastructure/000-index.md)에서 확인합니다.

여러 영역을 잇는 전체 구축 순서와 현재 live 진행 상태는
[운영 시나리오 목차](scenarios/000-index.md)가 소유합니다. 개별 runbook은 재사용 가능한
단계 절차만 소유하며 환경별 진행 snapshot을 누적하지 않습니다.

Runbook의 각 단계는 [150 문서화 워크플로우의 Procedure 실행 계약](../workflow/150-documentation-workflow.md#4-구조-gate)에 따라
언제·누가·어디서·무엇을·왜 실행하는지와 입력·완료·재개점을 명시합니다. 안전한 실행
진입점이 없는 단계는 명령을 지어내지 않고 중단 지점과 별도 구현 필요성을 표시합니다.

## 지금 하려는 작업으로 찾기

| 현재 상황과 사람의 목표 | 시작할 runbook |
| --- | --- |
| Staging 전체 구축 상태를 확인하고 마지막 확인 지점부터 재개 | [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md) |
| Platform 주요 상태를 확인하거나 이상 원인을 mutation 전에 분류 | [000 Platform 진단 목차](diagnostics/000-index.md) |
| Staging AWS 기반을 처음 만들기 | [AWS 최초 구축](100-aws-initial-provisioning-runbook.md) |
| 공통 실행 artifact를 EC2에 전달·검증·배치하기 | [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md) |
| 준비한 실행 artifact로 단일 node cluster 만들기 | [200 Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md) |
| GitHub CI·Sonar 품질 게이트를 처음 설정하기 | [GitHub CI 품질 게이트 최초 설정](310-github-ci-quality-configuration-runbook.md) |
| Repository CI부터 ECR·Argo CD delivery 기반까지 처음 붙이기 | [Repository·delivery platform 최초 구축](300-repository-and-delivery-platform-bootstrap-runbook.md) |
| Staging DB role·Secret·GitHub App·Application을 처음 연결하기 | [Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md) |
| 배포된 Staging Application에 최초 Super Admin을 생성하고 초기 password 변경 완료 | [360 Super Admin 최초 bootstrap](360-super-admin-bootstrap-runbook.md) |
| Staging API를 internet-facing ALB와 HTTPS domain으로 처음 공개하기 | [370 Staging 외부 HTTPS ingress 최초 구축](370-staging-external-https-ingress-bootstrap-runbook.md) |
| `main` CI 성공 image의 ECR digest 확보 | [GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md) |
| 현재 node가 private ECR exact digest를 pull·실행하는지 다시 점검 | [Private ECR pull 점검](410-private-ecr-image-pull-verification-runbook.md) |
| 새 digest를 staging에 배포하거나 이전 digest로 rollback | [GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md) |
| Staging에서 회원가입→RDS→실제 인증 메일→인증 완료 기능 경로를 선택 점검 | [430 Application 기능 qualification](430-application-functional-qualification-runbook.md) |
| 진행 중인 Kubernetes bootstrap을 의도적으로 취소 | [Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md) |
| 일반 사용자에게 cluster-admin kubeconfig 배포 | [Admin kubeconfig 배포](510-kubernetes-admin-kubeconfig-runbook.md) |
| GitHub App private key 교체 | [Argo CD repository credential 교체](520-argocd-repository-credential-rotation-runbook.md) |
| Sonar 분석 token 교체 | [Sonar token 교체](530-sonar-token-rotation-runbook.md) |
| Resend API key 교체·notification key 추가 또는 폐기 | [Application notification credential 변경](540-application-notification-credential-runbook.md) |

## `100` 최초 AWS 구축과 EC2 공통 실행 준비

먼저 `100`에서 공통 전제와 전체 경로를 확인하고, `110`~`140`의 AWS resource를 순서대로
확인한 뒤 `150`에서 공통 실행 artifact를 EC2에 배치합니다. 각 resource는 독립된
입력·성공·복구 경계를 가지므로 `110`~`140`에서는 앞 resource가 완료되기 전에 다음
단계로 넘어가지 않습니다. AWS 전체 완료 확인은 `100`으로 돌아가 판정합니다.

| 순서 | 절차 | 완료 결과 |
| --- | --- | --- |
| 진입·종료 | [AWS 최초 구축](100-aws-initial-provisioning-runbook.md) | 시작 전 공통 전제·순서를 읽고 하위 단계 뒤 전체 완료·handoff 확인 |
| `110` | [VPC 최초 구축](110-vpc-provisioning-runbook.md) | VPC, subnet, route, gateway, endpoint와 DNS |
| `120` | [EC2 Kubernetes node 최초 구축](120-ec2-node-provisioning-runbook.md) | SSH 가능한 Ubuntu ARM64 node |
| `130` | [Private ECR·IAM 최초 구축](130-ecr-iam-provisioning-runbook.md) | ECR, GitHub push role, EC2 pull instance profile |
| `140` | [RDS PostgreSQL 최초 구축](140-rds-provisioning-runbook.md) | Private database와 연결·backup·monitoring 확인 |
| `150` | [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md) | 공통 실행 artifact의 archive·checksum 검증과 EC2 고정 경로 배치 |

## `200` 최초 Kubernetes 구축

[150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)을 완료한 뒤
[200 Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)에서 다음 다섯 단계를 순서대로
실행합니다.

| 순서 | 절차 |
| --- | --- |
| `210` | [Host 준비](210-kubernetes-host-preparation-runbook.md) |
| `220` | [Containerd 설치](220-containerd-installation-runbook.md) |
| `230` | [Kubernetes component 설치](230-kubernetes-components-installation-runbook.md) |
| `240` | [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md) |
| `250` | [Calico network 설치](250-calico-network-installation-runbook.md) |

## `300` 최초 repository·delivery 기반 구축과 선택 초기 운영

[Repository·delivery platform 최초 구축](300-repository-and-delivery-platform-bootstrap-runbook.md)의
단계별 선행 조건과 순서를
확인한 뒤 필요한 단계를 실행합니다. `310`~`350`은 delivery 기반을, 선택 `360`은 최초
Super Admin 운영 준비를, `370`은 외부 HTTPS 공개를 소유합니다.

| 순서 | 절차 |
| --- | --- |
| `310` | [GitHub CI 품질 게이트 최초 설정](310-github-ci-quality-configuration-runbook.md) |
| `320` | [GitHub ECR 게시 최초 설정](320-github-ecr-publishing-configuration-runbook.md) |
| `330` | [Kubelet ECR credential provider 최초 설치](330-kubelet-ecr-credential-provider-installation-runbook.md) |
| `340` | [Argo CD Core 최초 설치](340-argocd-core-installation-runbook.md) |
| `350` | [Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md) |
| 선택 `360` | [Super Admin 최초 bootstrap](360-super-admin-bootstrap-runbook.md) |
| `370` | [370 Staging 외부 HTTPS ingress 최초 구축](370-staging-external-https-ingress-bootstrap-runbook.md) |

## `400` 평소 반복 delivery·점검

| 사람의 반복 작업 | 절차 |
| --- | --- |
| PR merge 뒤 자동 게시를 관찰하고 exact digest 확보 | [GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md) |
| Published digest의 node 인증·pull·실행 경로 점검 | [Private ECR pull 점검](410-private-ecr-image-pull-verification-runbook.md) |
| Digest 변경 PR 배포, 상태 확인과 revert PR rollback | [GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md) |
| Staging test member의 회원가입·실제 이메일 인증·안전한 cleanup | [430 Application 기능 qualification](430-application-functional-qualification-runbook.md) |

## `500` 구성 변경·복구

| 변경·복구 상황 | 절차 |
| --- | --- |
| Bootstrap을 정상 재실행하지 않고 의도적으로 취소 | [Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md) |
| 선택한 사용자에게 admin credential 배포 | [Admin kubeconfig 배포](510-kubernetes-admin-kubeconfig-runbook.md) |
| 최초 연결 뒤 GitHub App private key rotation | [Argo CD repository credential 교체](520-argocd-repository-credential-rotation-runbook.md) |
| 최초 설정 뒤 Sonar 분석 token rotation | [Sonar token 교체](530-sonar-token-rotation-runbook.md) |
| 최초 bootstrap 뒤 notification credential 변경 | [Application notification credential 변경](540-application-notification-credential-runbook.md) |
| Security Audit 독립 append 실패의 누락 범위·복구 가능성 판정 | [Security Audit 장애 복구](550-security-audit-failure-recovery-runbook.md) |

## Platform 진단

평소 baseline 확인과 이미 발생한 문제의 원인 분류는 같은 read-only 관찰 도구를 사용합니다.
관찰 깊이만 outline, 분야별 확장 묶음, 상세 해석 순서로 늘립니다.

| 상황 | 시작 문서 | 완료 결과 |
| --- | --- | --- |
| 장애 증상 없이 현재 상태를 확인 | [100 Kubernetes platform 핵심 진단 outline](diagnostics/100-kubernetes-platform-diagnostic-outline.md) | 주요 component baseline과 더 볼 영역 파악 |
| Timeout·접속 실패·resource pressure·sync 실패 발생 | [100 Kubernetes platform 핵심 진단 outline](diagnostics/100-kubernetes-platform-diagnostic-outline.md) | Read-only 증거에서 첫 fault domain을 찾고 상세 문서로 확장 |

`diagnostics/`는 자체 `000` router와 `100`~`180` 번호 그룹을 사용합니다. Node 수는
진단 문서 identity를 바꾸지 않으며 node별 조회만 대상마다 반복합니다. 환경별 node 수와
진행 상태는 scenario가 소유합니다.

Terraform은 현재 실행 가능한 운영 절차가 아닙니다. 수동 AWS resource의 import·replace,
state·검증·rollback을 결정하기 전에는 전환을 시작하지 않으며, 현재 경계는
[004 Infrastructure 진화 Backlog](../work-items/backlog/2026/004-infrastructure-evolution.md)가
미래 후보로 소유합니다. 현재 수동 구성 소유권은
[100 Infrastructure overview](../project/infrastructure/100-infrastructure-overview.md)에서 확인합니다.

각 초기·반복 runbook 안의 실패 표는 같은 사람 목표에서 재개할 checkpoint를 소유합니다.
Data 삭제, 다른 owner resource 인수, version migration처럼 현재 절차가 승인하지 않는 복구는
일반 재실행으로 처리하지 않습니다.

## 번호 그룹과 증거 경계

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | AWS 최초 구축과 EC2 공통 실행 준비 |
| `200`~`290` | Kubernetes 최초 구축 |
| `300`~`390` | Repository·delivery platform 최초 구축과 선택 초기 운영 |
| `400`~`490` | 평소 반복 delivery·점검 |
| `500`~`590` | 구성 변경·복구 |

`diagnostics/`는 위 root 번호대에 새 block을 예약하지 않고 directory 안에서 독립 번호를
사용합니다.

Runbook과 repository 검증은 실제 외부 resource의 성공 증거가 아닙니다. 각 작업의
성공·실패는 해당 runbook의 관찰 결과로 판정하고, 확인한 전체 live 상태와 재개 위치는
[Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에
반영합니다.

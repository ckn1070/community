# Argo CD Core 최초 설치 runbook

Kubernetes `1.36.2` 단일 ARM64 node cluster에 headless Argo CD Core `3.5.0`을 최초
설치하는 절차입니다. Application prerequisite와 private repository 최초 연결은
[Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)이
소유합니다.

Core·non-HA·UI 미설치와 고정 version을 선택한 이유는
[160 Argo CD platform](../project/infrastructure/160-argocd-platform.md)에서 확인합니다.
Artifact checksum, 고정 image digest, owner guard와 apply의 공개 계약은
[GitOps bootstrap README](../../scripts/bootstrap/gitops/README.md)가 소유합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Kubernetes bootstrap 완료 뒤, private repository를 연결하기 전에 최초 한 번 실행합니다. |
| 누가 | Target EC2에 SSH 접속하고 cluster-admin kubeconfig와 `sudo`를 사용할 수 있는 운영자가 실행합니다. |
| 기본 실행 위치 | EC2 terminal입니다. |
| 기본 시작 directory | 제한 없음 |
| 무엇을 | `sudo /home/ubuntu/car-community-scripts/bootstrap/gitops/100-install-argocd-core.sh` |
| 왜 | UI·API server 없이 GitOps reconciliation에 필요한 Core component와 CRD만 준비하기 위해서입니다. |
| 완료·재개 | Script exit `0`, 완료 메시지와 Core rollout을 확인합니다. Partial apply는 같은 script로 재개하고 owner·version 충돌은 결정 전 중단합니다. |

## 실행 전 확인

- [Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)의 다섯 live 단계가
  완료됐습니다.
- [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)을 완료해
  Argo CD bootstrap script가 고정 절대 경로에 있습니다.
- `sudo`를 사용할 수 있고 `/etc/kubernetes/admin.conf`로 API server readyz를 확인할 수
  있습니다.
- EC2에서 `raw.githubusercontent.com`, `quay.io`, `public.ecr.aws`에 outbound 접근할 수
  있습니다.
- `argocd` namespace가 없거나 이 bootstrap의
  `app.kubernetes.io/managed-by=car-community-argocd-core-bootstrap` label을 갖습니다.
- `argocd-server`가 있는 full 설치, 다른 owner의 namespace, 다른 Argo CD version·digest를
  현재 script로 인수·전환하지 않습니다.

## 실행

EC2 terminal의 어느 directory에서나 실행합니다.

```bash
sudo /home/ubuntu/car-community-scripts/bootstrap/gitops/100-install-argocd-core.sh
```

Script가 exit code `0`으로 끝나고 다음 메시지를 출력해야 합니다.

```text
[완료] Argo CD Core v3.5.0 component와 CRD를 확인했습니다.
```

추가 read-only 확인이 필요하면 EC2에서 실행합니다.

```bash
# 1. Argo CD Core workload 상태 조회
# 확인: applicationset-controller·redis·repo-server·application-controller가 Ready
printf '\n\n========== [1/2] Argo CD Core workload ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment,statefulset -n argocd

# 2. UI·API server 부재 조회
# 확인: 빈 출력, 즉 deployment/argocd-server 없음
printf '\n\n========== [2/2] argocd-server 부재 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/argocd-server -n argocd --ignore-not-found
```

1. 첫 조회는 Core의 네 workload가 실제로 Ready인지 확인합니다.
2. 두 번째 조회는 Core 설치에 포함하지 않은 UI·API server가 생기지 않았는지 확인합니다.
   출력이 없어야 하며, 상태 확인을 위해 port-forward·ALB를 추가하지 않습니다.

## 중단과 복구

| 중단 상태 | 자동으로 하지 않는 일 | 확인·복구 | 재시작 |
| --- | --- | --- | --- |
| API readyz·kubeconfig 실패 | Cluster reset·kubeconfig 교체 | [160 Kubernetes control plane 진단](diagnostics/160-kubernetes-api-control-plane-and-node.md)으로 실제 상태를 분류합니다. | 원인 해결 뒤 같은 script |
| Node가 단일 ARM64가 아님 | 다른 architecture digest 자동 선택 | 현재 EC2와 node architecture를 확인하고 지원 범위 변경을 별도 설계합니다. | 현재 script 사용 안 함 |
| Manifest download·checksum 실패 | 다른 version·stable URL 사용 | [150 Linux network 진단](diagnostics/150-linux-network.md)으로 DNS·outbound를 확인하고 official tag byte·repository 고정 hash를 대조합니다. | 원인 해결 뒤 같은 script |
| 다른 owner의 `argocd` namespace | Resource 인수·label 보정 | 기존 namespace와 resource owner를 확인하고 migration 여부를 별도 결정합니다. | 결정 전 중단 |
| `argocd-server` 발견 | Full→Core 삭제 전환 | 기존 설치 owner와 UI/API 사용 여부를 별도 migration에서 결정합니다. | 결정 전 중단 |
| 다른 Argo CD·Redis image 발견 | 자동 upgrade·downgrade | 지원 표·upgrade note, target version·digest를 별도 변경 작업에서 확정합니다. | 현재 script 사용 안 함 |
| Apply·rollout 실패 | Namespace·CRD·Pod 자동 삭제 | [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)으로 event·component log와 resource 원인을 분류합니다. | 원인 해결 뒤 같은 script |

같은 owner와 exact digest의 partial apply는 같은 script 재실행으로 수렴시킵니다. 실패했다는
이유만으로 `argocd` namespace나 CRD를 삭제하지 않습니다. 의도적 uninstall·owner 인수·
version migration은 현재 runbook의 복구 범위가 아닙니다.

## 완료와 다음 단계

- Core component와 CRD가 script postcondition을 통과했습니다.
- 실제 component spec·Pod `imageID`가 공개 계약의 exact digest입니다.
- `argocd-server`가 없습니다.

Local source·contract test는 cluster rollout을 대신하지 않습니다. Private repository,
RDS·application prerequisite 입력, GitHub App과 non-empty staging overlay가 준비됐으면
[Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)으로
진행합니다. 준비되지 않았다면 Core를 Ready 상태로 두고 외부 전제부터 완료합니다.

## 관련 문서

- **현재 Argo CD 구조와 선택**: [160 Argo CD platform](../project/infrastructure/160-argocd-platform.md)
- **Script 공개 계약**: [GitOps bootstrap README](../../scripts/bootstrap/gitops/README.md)
- **다음 구축 절차**: [Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)
- **Argo 진단**: [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)

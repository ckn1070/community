# GitOps application 배포와 rollback runbook

Private ECR의 exact image digest를 현재 `car-community` repository의
`deploy/overlays/stg` 목표 상태로 변경하는 pull request를 review·merge하고, Argo CD 자동
sync와 workload 상태를 확인하는 반복 운영 절차입니다. Rollback도 live resource 직접
변경이 아니라 이전 digest로 되돌리는 revert pull request로 수행합니다.

Source→image→GitOps→cluster actor와 자동화·수동 경계는
[GitOps application delivery](../project/infrastructure/210-gitops-application-delivery.md)에서
확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | 최초 Application bootstrap이 완료된 뒤 새 exact digest를 staging에 배포하거나 이전 digest로 되돌릴 때 반복합니다. |
| 누가 | Repository PR 권한과 target EC2 SSH·cluster read 권한을 가진 운영자가 실행합니다. |
| 어디서 | Manifest 편집·render·PR은 local repository root, live 확인은 SSH로 접속한 EC2 terminal에서 수행합니다. |
| 무엇을 | `deploy/overlays/stg/kustomization.yaml`의 digest 변경, `kubectl kustomize`, target dry-run, pull request와 표준 `kubectl` read-only 확인을 사용합니다. 현재 digest 변경 전용 repository script는 없습니다. |
| 왜 | Git PR을 배포 승인·이력·rollback의 단일 경계로 유지하고 live resource 직접 변경을 막기 위해서입니다. |
| 완료·재개 | Merge revision이 `Succeeded|Synced|Healthy`이고 Pod imageID가 목표 digest면 완료입니다. 실패하면 Git forward-fix 또는 revert PR에서 재개합니다. |

## 실행 prerequisite와 현재 상태 확인

현재 repository에는 staging Kustomize manifest가 구현돼 있지만 digest 변경 PR 자동화는
없습니다. 따라서 표준 `kubectl kustomize`와 target cluster의 server-side dry-run으로
직접 확인한 뒤 이 manual 절차를 실행합니다.

- `deploy/overlays/stg`가 존재하고 non-empty resource를 render합니다.
- `deploy/overlays/stg/kustomization.yaml`의 `images[].digest`가 application image reference를
  소유합니다.
- Runtime·migrator DB role과 Git 밖 Namespace·네 Secret, Git의 actual DB URL·RDS CA·notification
  ConfigMap source가 준비됐습니다.
- `main` 변경은 pull request review 뒤 merge하는 운영 정책을 따릅니다. 현재 GitHub branch
  protection이 이를 강제하지 않으므로 운영자가 direct push와 force push를 사용하지
  않아야 합니다.
- [350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)이
  fresh reconcile과 최초 `Synced`·`Healthy` 확인까지 완료된 후에만 이 반복 배포
  runbook을 시작합니다.

현재 staging의 live 진행 상태와 재개 위치는
[100 Staging single-node bootstrap scenario](scenarios/100-staging-single-node-bootstrap.md)가
소유합니다. 이 runbook은 최초 bootstrap이 완료됐다고 단정하지 않으며, scenario에서
fresh `Synced`·`Healthy`가 확인되지 않았다면 이 절차를 시작하지 않습니다.

기존 image owner를 식별하지 못하거나 overlay가 비어 있으면 중단합니다. 이 runbook이 exact
file·Kustomize field·검증 command를 추측해 새 manifest 계약을 만들지 않습니다.

## 배포 입력

[GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)에서 다음을 모두 확인합니다.

- 게시 source의 exact 40자리 `main` SHA
- ECR Console과 job summary가 함께 확인한 full exact digest reference
- 현재 배포하려는 PR·변경과 위 source revision의 대응 관계

Mutable tag나 `latest`를 배포 입력으로 사용하지 않습니다.

## 배포 실행과 확인

1. 현재 `car-community` repository의 `deploy/overlays/stg/kustomization.yaml`을 확인합니다.
   `deploy/overlays/stg` 아래에 `pending.` 또는 `pending.invalid` sentinel이 하나라도 있으면
   actual RDS endpoint·notification sender·frontend URL을 승인된 값으로 바꾸기 전에는
   배포하지 않습니다.

   ```bash
   ! rg -n 'pending\.' deploy/overlays/stg
   ```
2. `images[].digest`만 게시 결과의 full exact ECR digest로 변경하고 local에서 render합니다.

   ```bash
   kubectl kustomize deploy/overlays/stg > /tmp/car-community-stg.yaml
   ```

   현재 cluster API schema·admission까지 확인할 때는 render 결과만 SSH stdin으로 보내
   server-side dry-run을 실행합니다. Resource는 생성하지 않습니다.

   ```bash
   env -i HOME="${HOME}" PATH="${PATH}" \
     ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
     'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' \
     < /tmp/car-community-stg.yaml
   ```
3. 변경된 digest, 대상 `stg` overlay와 예상 rollout을 pull request에서 review합니다.
4. Pull request를 `main`에 merge합니다. Image 게시만으로 merge·배포 승인을 대신하지
   않습니다.
   이 `deploy/**` 전용 merge에도 `CI`와 publish workflow의 경로 분류는 실행될 수 있지만
   AWS OIDC·image build·push job은 skip돼야 합니다.
5. Argo CD가 merge된 목표 상태를 자동 sync한 뒤 EC2에서 read-only 상태를 확인합니다.

```bash
EXPECTED_REVISION='<merge된 40자 main commit SHA>'

APPLICATION_STATE="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{.status.sync.revision}{"|"}{.status.operationState.phase}{"|"}{.status.sync.status}{"|"}{.status.health.status}')"
test "${APPLICATION_STATE}" = "${EXPECTED_REVISION}|Succeeded|Synced|Healthy"

APPLICATION_CONDITIONS="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.conditions[*]}{.type}{"="}{.message}{"\n"}{end}')"
test -z "${APPLICATION_CONDITIONS}"

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.operationState.syncResult.resources[*]}{.syncPhase}{"\t"}{.hookPhase}{"\t"}{.kind}{"\t"}{.name}{"\t"}{.status}{"\n"}{end}'

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status deployment/car-community -n car-community --timeout=300s

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get pods -n car-community \
  -o custom-columns='NAME:.metadata.name,READY:.status.containerStatuses[*].ready,IMAGE_ID:.status.containerStatuses[*].imageID'
```

첫 출력의 revision이 `${EXPECTED_REVISION}`, operation `Succeeded`, `Synced`, `Healthy`와
일치하고 Application condition이 없어야 합니다. 두 번째 출력에서 단일 Migration Job과
Deployment 순서, migration hook 성공을 확인합니다. 성공한 hook Job은 삭제될 수
있으므로 현재 Application operation 결과를 사용합니다. Target Pod rollout과 imageID도 목표
digest와 일치해야 합니다. UI는 설치하지 않았으므로
상태 확인을 위해 `argocd-server`, port-forward나 ALB를 추가하지 않습니다.

## 배포 중단과 복구

| 상태 | 대응 |
| --- | --- |
| Overlay 또는 image reference owner를 식별할 수 없음 | PR을 만들지 않고 현재 repository의 `deploy/` manifest owner·구조를 먼저 확정합니다. |
| PR 검증·review 실패 | Merge하지 않습니다. 현재 repository의 `deploy/` manifest나 digest 입력을 수정합니다. |
| PreSync Migration Job 실패 | Selective sync나 `flyway repair`로 우회하지 않습니다. Job log, RDS TLS·credential·schema·runtime 권한 callback 상태를 복구한 뒤 full sync합니다. |
| Merge 뒤 Application이 `Synced`가 아님 | Live Application spec을 수정하지 않습니다. [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)으로 condition, repository revision/path와 controller log를 확인하고 Git 목표 상태 또는 credential 원인을 수정합니다. |
| Application이 `Healthy`가 아니거나 Pod 실패 | Live Deployment image를 수정하지 않습니다. [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)으로 event·image pull·readiness를 확인하고 forward-fix PR 또는 아래 rollback PR을 선택합니다. Runtime 자체가 이상하면 [140 Linux node service 진단](diagnostics/140-systemd-journal-and-node-services.md)을 사용합니다. |
| 잘못된 빈 목표 상태 | Automated sync나 `allowEmpty: false`를 끄지 않습니다. 현재 repository overlay를 복구하는 PR을 우선합니다. |

## Rollback

1. 마지막 정상 배포의 exact digest commit과 현재 문제 digest commit을 `car-community`
   이력에서 확인합니다.
2. 문제 digest 변경 commit을 되돌리는 revert pull request를 만듭니다.
3. Revert 내용이 이전 exact digest를 복원하는지 review하고 merge합니다.
4. 위 read-only 명령으로 Argo CD가 되돌린 목표 상태를 `Synced`·`Healthy`로 보고하고 Pod가
   정상 상태인지 다시 확인합니다.

Argo CD Application, live Deployment image 또는 Pod를 직접 수정하지 않습니다. Git revert가
배포 이력과 목표 상태의 canonical 복구 경로입니다. Revert merge 뒤에도 실패하면 추가 live
mutation을 하지 말고 Application condition·Pod event·image pull과 이전 digest의 실제 ECR
존재 여부를 조사합니다. Database migration은 Git revert로 되돌아가지 않으므로 이전 image가
현재 schema와 호환되는지도 rollback 전에 확인합니다.

## 완료 판정

- 배포 또는 rollback의 exact digest 변경이 review된 PR로 `main`에 merge됐습니다.
- `car-community-stg` Application이 merge된 목표 상태를 `Synced`·`Healthy`로 보고합니다.
- 단일 Migration PreSync와 runtime Deployment 순서가 성공했습니다.
- Target namespace Pod를 read-only로 확인했습니다.
- Live Deployment·Application 직접 변경이나 credential 우회를 만들지 않았습니다.

이 절차는 현재 repository의 `deploy/` manifest와 live cluster가 실제 존재할 때만 완료할 수
있습니다. 문서·script 정적 검증만으로 GitOps 배포 성공을 주장하지 않습니다.

## 관련 문서

- **현재 반복 전달 개념**: [GitOps application delivery](../project/infrastructure/210-gitops-application-delivery.md)
- **Image 게시와 digest 확보**: [GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)
- **최초 application 구축**: [Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)
- **Credential 교체**: [Argo CD repository credential 교체](520-argocd-repository-credential-rotation-runbook.md)
- **Argo 진단**: [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)
- **Workload network 진단**: [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)

# Kubernetes application desired state

이 directory는 Argo CD가 `main`에서 읽어 staging Kubernetes resource로 수렴시키는
application desired state를 소유합니다. 구성요소의 작동 원리와 선택 이유는
[220 GitOps application delivery](../docs/project/infrastructure/220-gitops-application-delivery.md),
실제 digest 변경·배포·rollback 절차는
[420 GitOps 배포·rollback runbook](../docs/operations/420-gitops-deployment-and-rollback-runbook.md)이
소유합니다.

## Directory 책임

| 경로 | 소유하는 내용 | 소비자·변경 주체 |
| --- | --- | --- |
| `base/` | Deployment, Service와 PreSync Migration Job의 환경 공통 구조 | Staging overlay가 조합하고 GitOps pull request가 변경 |
| `overlays/stg/` | Namespace, exact ECR digest, 공개 application 설정, `production,staging` application profile, NodePort patch와 public Ingress | `car-community-stg` Argo CD Application이 `main`에서 render·sync |
| `overlays/stg/pre-sync/` | Git으로 관리하는 DB URL과 pinned RDS CA ConfigMap | Migration Job과 runtime Deployment가 mount·참조 |

AppProject와 Application 자체, repository credential은
`scripts/bootstrap/gitops`가 최초 bootstrap desired state로 소유합니다. Namespace와
runtime·migrator·auth·notification Secret은 Git 밖 bootstrap 입력이며 이 directory에
저장하지 않습니다.

## 변경 경계

- `base`의 `registry.invalid/car-community`는 environment overlay가 실제 image로 교체하지
  않으면 pull할 수 없게 하는 fail-close 기본값입니다.
- Staging image는 mutable tag가 아니라 ECR full digest로 고정하고, source image 게시와
  별도의 `deploy/**` pull request에서 deployment intent를 승인합니다.
- Argo CD 최초 연결 뒤 manifest 변경과 rollback은 review된 Git 변경으로 수행합니다.
  Live resource를 직접 수정해 Git desired state와 다른 장기 상태를 만들지 않습니다.
- Secret actual value, local `.env`, kubeconfig와 cloud credential은 commit하지 않습니다.
- Application Deployment만 `production,staging` profile을 사용해 관리자용 Swagger를
  활성화합니다. PreSync Migration Job은 web endpoint가 필요 없으므로 `production`을
  유지합니다.
  공개 설정과 Git 밖 Secret의 owner를 바꿀 때에는 application bootstrap·rotation 절차와
  consumer를 함께 확인합니다.
- Ingress는 application routing intent만 소유합니다. Controller release·IAM·subnet tag·ACM
  certificate·Cloudflare DNS lifecycle은
  [170 AWS Load Balancer ingress](../docs/project/infrastructure/170-aws-load-balancer-ingress.md)와
  해당 runbook이 소유합니다.

## Render와 배포 확인

Repository root에서 staging overlay가 하나의 desired state로 render되는지 확인합니다.

```bash
kubectl kustomize deploy/overlays/stg
```

이 명령은 local render만 확인하며 target API admission, Argo CD reconciliation과 live
workload 성공을 증명하지 않습니다. Target server-side dry-run, pull request merge,
Argo CD revision·health와 Pod image 확인은
[420 GitOps 배포·rollback runbook](../docs/operations/420-gitops-deployment-and-rollback-runbook.md)의
현재 절차를 따릅니다.

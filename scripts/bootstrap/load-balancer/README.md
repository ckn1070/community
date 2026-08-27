# AWS Load Balancer Controller bootstrap

이 디렉터리는 기존 staging 단일 node kubeadm cluster에 AWS Load Balancer Controller를
최초 구성하는 script를 소유합니다. 구조·IAM·network·TLS 선택의 현재 기준은
[170 AWS Load Balancer ingress](../../../docs/project/infrastructure/170-aws-load-balancer-ingress.md),
실제 AWS Console·EC2·Cloudflare 실행 순서는
[370 Staging 외부 HTTPS ingress 최초 구축](../../../docs/operations/370-staging-external-https-ingress-bootstrap-runbook.md)이
소유합니다.

Controller는 application manifest가 아니라 cluster platform dependency입니다. 따라서
Argo CD가 자기 실행 기반을 관리하게 하지 않고 운영자가 verified ephemeral Helm client로
bootstrap합니다. Helm은 CRD 이외의 RBAC·ServiceAccount·webhook TLS·IngressClass와
controller workload를 official chart 계약으로 함께 관리합니다. Host에 Helm을 영구 설치하지
않아 최초 구성 뒤 불필요한 mutable executable을 남기지 않습니다.

## `100-install-aws-load-balancer-controller.sh` 공개 계약

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 새 node IAM role/profile, IMDSv2, public subnet tag와 ACM certificate를 runbook대로 준비한 뒤 application Ingress를 merge하기 전에 직접 실행합니다. |
| 실행 | EC2 전달 root에서 `sudo ./bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh <VPC_ID>`를 실행합니다. `<VPC_ID>`는 AWS Console에서 확인한 long-form ID 하나입니다. |
| 지원 범위 | Ubuntu `24.04`, Linux `arm64`, 단일 Ready node, kubelet·cluster `v1.36.2`, cluster `stg-car-community-k8s`, region `ap-northeast-2`입니다. |
| node identity | IMDSv2 token required와 instance profile role `stg-car-community-k8s-node-role`을 요구합니다. 같은 token으로 instance ID와 AZ를 읽어 providerID `aws:///<AZ>/<INSTANCE_ID>`를 만듭니다. |
| 직접 변경 | 빈 node providerID의 exact EC2 identity 설정, chart CRD server-side apply, `kube-system` Helm release 설치, controller 검증 뒤 외부 Load Balancer 제외 node label 제거입니다. |
| 변경하지 않음 | IAM policy·role·profile, IMDS option, subnet·tag, Security Group, ACM certificate, Kubernetes Service·Ingress, ALB·Target Group·listener, Cloudflare DNS를 생성하거나 변경하지 않습니다. |
| credential | EC2 instance profile 임시 credential을 저장하거나 조회하지 않습니다. IMDSv2 token은 curl argv가 아닌 stdin header로 전달하며 script 종료 뒤 보존하지 않습니다. |
| 성공 | Exact Helm release·Deployment image·replica·argument, Pod imageID·Ready, CRD·webhook·Service·IngressClass와 node label 부재를 확인한 exit `0`입니다. |

Controller Pod가 node IMDS를 사용할 수 있는 response hop limit `2`, 새 role에 연결된 exact ECR
pull·LBC policy 내용과 public subnet의 route·free IP·tag는 local node API만으로 authoritative하게
확인할 수 없습니다. Script는 이를 추측하거나 AWS API로 변경하지 않으며 runbook의 AWS
preflight와 후속 ECR pull·ALB reconciliation 검증이 완료 조건을 보완합니다.

## Exact artifact와 설치 값

| Artifact·값 | Exact 계약 |
| --- | --- |
| Helm client | `3.21.4`, `https://get.helm.sh/helm-v3.21.4-linux-arm64.tar.gz` |
| Helm SHA-256 | `b54c04b4e0b2540bbdc08c17a121dab70e9a2ed0de5705528fec68a5fd3b85a7` |
| LBC chart | `3.5.0`, `https://aws.github.io/eks-charts/aws-load-balancer-controller-3.5.0.tgz` |
| Chart SHA-256 | `45051f634b33e10baccb3354d0681b7de787c60445e599fa276e0c9aedd4ccd5` |
| ARM64 image | `public.ecr.aws/eks/aws-load-balancer-controller:v3.5.0@sha256:1ee3cc54fb62248bd56f2157f7be79baba2be8f0bbf2d926e2650d7ba710ee5f` |
| Helm owner | Release `aws-load-balancer-controller`, namespace `kube-system` |
| Chart values | Exact `clusterName`, `region`, runtime VPC ID |
| Runtime input | AWS Console에서 확인한 exact long-form VPC ID |
| 규모 | `replicaCount=1`, autoscaling disabled, controller HPA 부재 |
| Chart owner | ServiceAccount·RBAC 생성, cert-manager 사용 안 함, webhook TLS Secret 유지 |
| Network | IngressClass `alb`, default target type `instance`, custom frontend SG 없음, backend SG rule management는 chart 기본값 유지 |

Calico VXLAN overlay Pod IP는 VPC에서 직접 routable하지 않으므로 현재 baseline은 `instance`
target을 사용합니다. ALB target은 node의 auto-assigned NodePort입니다. Self-managed kubeadm에는
AWS cloud-controller-manager가 없으므로 providerID가 자동 설정됐다고 가정하지 않습니다.
Script는 singleton node의 providerID가 비어 있을 때만 exact IMDS identity로 patch하고, 이미
exact면 변경하지 않으며, 다른 nonempty 값이나 조회 실패는 자동 인수하지 않습니다.

## 실행 순서와 mutation boundary

1. Root, 외부 command, Ubuntu·ARM64, API `/readyz`, kubeadm cluster name·version과 exact
   singleton Ready node를 확인합니다.
2. IMDSv2 token required, exact node role·instance ID·AZ와 현재 providerID의 변경 가능성을
   확인합니다.
3. Temporary directory에 Helm과 chart를 bounded download하고 각 SHA-256을 확인합니다.
4. Existing Helm release·resource owner·chart label·image·replica가 없거나 exact 계약인지
   확인합니다. Foreign owner나 다른 version을 upgrade·downgrade하지 않습니다.
5. 빈 node providerID를 exact EC2 identity로 설정하고 결과를 다시 읽습니다.
6. Chart의 일반 CRD와 Gateway CRD를 Helm보다 먼저 fixed field manager로 server-side
   apply하고 required CRD 7개의 `Established`를 기다립니다. Helm upgrade는 CRD를
   갱신하지 않고 CRD 생성·discovery는 비동기이므로 이 순서와 barrier를 고정합니다.
7. 모든 Helm cluster 호출에 `/etc/kubernetes/admin.conf`를 명시해 exact value로
   `upgrade --install --atomic --wait`를 실행합니다.
8. Release·Deployment·Pod·CRD·webhook·IngressClass postcondition을 확인한 뒤 마지막
   mutation으로 `node.kubernetes.io/exclude-from-external-load-balancers` label을 제거하고
   부재를 다시 확인합니다.

ProviderID 설정은 instance target registration에 필요한 platform identity입니다. 제외 label은
controller rollout이 실패한 node를 외부 target 후보로 먼저 열지 않기 위해 controller 전체
postcondition 뒤에만 제거합니다.

## 재실행·실패 경계

- 별도 완료 marker는 사용하지 않습니다. 같은 exact release는 owner와 live version을 확인한
  뒤 같은 목표로 다시 `upgrade --install`할 수 있습니다.
- Download·checksum 실패는 final artifact를 남기지 않습니다. Temporary Helm·chart는 성공과
  실패 모두 종료 trap에서 삭제합니다.
- Helm은 `--atomic`으로 실패 release를 정상 완료처럼 남기지 않습니다. CRD apply 뒤 Helm이
  실패했다면 원인을 해결하고 같은 exact script를 재실행합니다.
- Exact providerID와 이미 제거된 label은 no-op입니다. 빈 providerID만 설정하며 다른 nonempty
  providerID는 자동 복구하지 않습니다.
- Foreign resource owner, 다른 chart label·image·replica, Helm release와 Deployment가
  어긋난 부분 상태 또는 fixed replica를 바꿀 수 있는 기존 HPA는 자동 인수하지 않습니다.
  별도 platform migration·복구 결정을 먼저 내립니다.
- Controller upgrade·uninstall, IAM rotation과 전체 ingress 철거는 이 최초 bootstrap의
  재실행이 아니라 별도 versioned platform operation입니다.

## Local 검증

```bash
bash -n \
  scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh \
  scripts/bootstrap/load-balancer/tests/test-100-install-aws-load-balancer-controller.sh

shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/load-balancer/100-install-aws-load-balancer-controller.sh \
  scripts/bootstrap/load-balancer/tests/test-100-install-aws-load-balancer-controller.sh

scripts/bootstrap/load-balancer/tests/test-100-install-aws-load-balancer-controller.sh
```

Local contract test는 exact artifact·checksum, bounded download, ephemeral Helm, live-confirmed
Ready JSONPath, exact kubeadm cluster identity, IMDS token argv 비노출, exact node
role·providerID, extraction 실패 전파, CRD Established barrier, controller HPA 부재, Helm
kubeconfig·value, owner·version guard와 providerID→CRD→controller→label의 필수 상대 순서를
확인합니다. Direct AWS CLI와 내장 application Ingress manifest의 부재는 보조 source gate로
확인하며, 전체 비변경 경계는 source review와 실제 EC2 runbook postcondition까지 완료해야만
판정합니다.

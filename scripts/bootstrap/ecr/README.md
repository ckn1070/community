# Private ECR node bootstrap script

이 디렉터리는 이미 구축된 Kubernetes node에 official ECR credential provider를 설치하고
kubelet이 EC2 instance profile의 임시 credential을 사용하도록 **최초 구성**하는 script를
소유합니다. 실제 private image pull 점검은
[verify/ecr](../../verify/ecr/README.md)이 소유합니다. ECR·IAM 구성 원리는
[ECR and IAM foundation](../../../docs/project/infrastructure/120-ecr-iam-foundation.md),
반복 image delivery 원리는
[Container image delivery](../../../docs/project/infrastructure/200-container-image-delivery.md)를
확인합니다. 실제 최초 구성은
[ECR and IAM provisioning](../../../docs/operations/130-ecr-iam-provisioning-runbook.md),
[GitHub ECR publishing configuration](../../../docs/operations/320-github-ecr-publishing-configuration-runbook.md),
[Kubelet ECR credential provider installation](../../../docs/operations/330-kubelet-ecr-credential-provider-installation-runbook.md),
반복 pull 검증은
[Private ECR image pull verification](../../../docs/operations/410-private-ecr-image-pull-verification-runbook.md)이
소유합니다.

## `100-configure-kubelet-credential-provider.sh` 공개 계약

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 Kubernetes bootstrap `100`~`500`, EC2 pull IAM instance profile 연결과 ECR image publish를 완료한 뒤 실행합니다. |
| 실행 | EC2 전달 root에서 `sudo ./bootstrap/ecr/100-configure-kubelet-credential-provider.sh`; 인자·환경변수 없음 |
| 지원 범위 | Ubuntu `24.04`, `arm64`, systemd, kubelet package `1.36.2-2.1` |
| provider | Official AWS cloud-provider binary `v1.36.1`, ARM64 URL과 고정 SHA-256 `96b58e...ed62` |
| 직접 변경 | Provider binary, credential provider config, `/etc/default/kubelet`, kubelet restart |
| credential | Secret을 저장하지 않으며 provider가 AWS SDK credential chain으로 EC2 instance profile 임시 credential을 사용합니다. |
| 성공 | Artifact checksum·mode, exact kubelet defaults, active kubelet process의 두 provider argument를 `/proc`에서 확인합니다. |
| 변경하지 않음 | IAM, ECR repository, Kubernetes Secret·ServiceAccount, application resource를 변경하지 않습니다. |

| 게시 path | mode |
| --- | --- |
| `/usr/local/libexec/kubernetes/kubelet-credential-providers/ecr-credential-provider` | `0755` |
| `/etc/kubernetes/image-credential-provider-config.yaml` | `0644` |
| `/etc/default/kubelet` | `0644` |

Kubernetes `1.36.2`와 같은 release line의 검토된 provider `1.36.1`을 고정하며 실행일 최신
version을 자동 선택하지 않습니다. Download는 연결 `15s`, 전체 `300s`, transient retry
`3회`와 간격 `2s`로 제한합니다. Timeout·HTTP `408`·`429`·일부 `5xx`처럼 재실행으로
회복 가능한 실패만 `curl` 기본 retry 대상으로 둡니다. `404`, 인증 오류와 checksum
불일치는 재시도하지 않고 source URL·권한·고정값을 바로 확인합니다.

최초 실행은 absent·empty 상태와 kubelet package가 게시하는 빈 기본값
`KUBELET_EXTRA_ARGS=`를 안전한 미구성 상태로 허용합니다. 재실행은 script가 게시한 exact
`/etc/default/kubelet`도 허용합니다. 그 밖의 인자·binary·configuration은 자동 병합하거나
덮어쓰지 않습니다. 같은 목표의 harmless한 download·file 게시·kubelet restart는 반복할 수
있으며 별도 marker로 생략하지 않습니다. Lock으로 동시 실행을 막습니다.

## Local 검증

```bash
bash -n \
  scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh \
  scripts/bootstrap/ecr/tests/test-100-configure-kubelet-credential-provider.sh

shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/ecr/100-configure-kubelet-credential-provider.sh \
  scripts/bootstrap/ecr/tests/test-100-configure-kubelet-credential-provider.sh

scripts/bootstrap/ecr/tests/test-100-configure-kubelet-credential-provider.sh
```

Local 검증은 render, checksum failure, absent·empty·package 기본 빈 kubelet 설정·exact
재실행과 foreign argument guard, mutation 순서만 증명합니다. 실제 EC2 credential·kubelet
process는 target에서 script postcondition으로만 완료 판정합니다.

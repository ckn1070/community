# Kubernetes bootstrap 취소

이 문서는 정상 오류 복구와 bootstrap 취소가 다른 이유, 각 단계에서 되돌릴 수 있는 범위와
data 손실 경계를 설명합니다. 실제 backup 확인·복원 명령은
[Kubernetes bootstrap 취소 runbook](../../operations/500-kubernetes-bootstrap-cancellation-runbook.md)이
소유합니다.

## 언제 취소 lifecycle인가

- 아직 service workload가 없는 host에서 같은 목표로 재실행하지 않고 bootstrap 자체를
  중단하기로 결정했습니다.
- 현재 file·package·service·data owner와 backup 내용을 실제로 확인했습니다.
- 실행 중 cluster·workload가 있다면 이 취소 경로를 사용하지 않습니다.

Backup file이 있다는 사실만으로 전체 이전 상태가 복원된다고 가정하지 않습니다. Package
upgrade, runtime image·snapshot, Kubernetes PKI·etcd와 cluster resource는 몇 개의 file
backup으로 되돌아가지 않습니다.

## 단계별 복원 가능 범위

| 단계 | Bootstrap이 보존한 것 | 제한적으로 가능한 것 | 자동으로 하지 않는 것 |
| --- | --- | --- | --- |
| Host `100` | 기존 `/etc/fstab`, 기존 project sysctl file backup | 실제 원본을 확인한 fstab·project sysctl 복원과 effective state 재적용 | APT package downgrade, 모르는 swap unit 변경 |
| Containerd `200` | 기존 project containerd config·modules-load file backup | 실제 owner·원본을 확인한 file 복원, hold·service lifecycle 조정 | `overlay` unload, `/var/lib/containerd` 삭제, package·runc 무조건 제거 |
| Component `300` | Cluster state는 만들지 않음 | Package·hold·repository·kubelet enabled actual state 조사 | Consumer 확인 없는 package·keyring·source 제거 |
| Control plane `400` | 일반 file backup으로 완전 복원 불가 | PKI·kubeconfig·etcd·data 영향 조사 | File 직접 삭제, 일반 `kubeadm reset`, init 반복 |
| Calico `500` | Declarative cluster network state | 별도 migration·upgrade·rollback 설계 | 다른 CNI 전환, field 강제 인수, workload 영향 없는 것으로 추정 |

기존 project sysctl·containerd file이 원래 없고 backup도 없다면 현재 file의 관리 표식과
script source를 대조해 bootstrap owner임을 확인해야 합니다. 다른 owner가 사용할 가능성이
있으면 삭제하지 않습니다.

## Control plane 이후가 destructive한 이유

`admin.conf` 없이 PKI·manifest·kubelet·etcd marker가 남은 partial state나 이미 생성된
cluster는 host file 복원으로 되돌릴 수 없습니다. Reset은 certificate·kubeconfig·local etcd,
node state와 workload data에 영향을 줄 수 있습니다. 따라서 다음 중 하나를 명시적으로
고르는 별도 복구가 필요합니다.

- Existing state를 조사·복구합니다.
- Data 영향과 backup을 확인하고 reset합니다.
- Resource를 새로 만들고 migration·재구축합니다.

Calico 이후에도 CNI resource·field ownership과 workload network가 있으므로 bootstrap 취소
명령 하나로 일반화하지 않습니다.

## 완료의 의미

취소 runbook 전체를 순서대로 실행하는 것이 완료가 아닙니다. 취소할 exact 단계만 선택하고
다음을 확인해야 합니다.

- 다른 단계·owner의 file·package·service·data를 변경하지 않았습니다.
- 사용한 backup이 실제 기존 상태였고 복원 뒤 effective state를 검증했습니다.
- 문서가 소유하지 않는 package·runtime·cluster data는 별도 work로 명시했습니다.

일반 실패는 해당 bootstrap 단계의 정상 재실행으로 돌아갑니다. 공통 판정은
[변경과 복구 원칙](300-change-and-recovery-principles.md)이 소유합니다.

## 관련 문서

- **실제 취소 절차**: [Kubernetes bootstrap 취소 runbook](../../operations/500-kubernetes-bootstrap-cancellation-runbook.md)
- **정상 cluster state model**: [Kubernetes cluster](140-kubernetes-cluster.md)
- **공통 재실행·변경 판정**: [변경과 복구 원칙](300-change-and-recovery-principles.md)

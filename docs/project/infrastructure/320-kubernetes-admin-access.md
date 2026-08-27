# Kubernetes admin access

이 문서는 kubeadm의 admin kubeconfig가 가진 권한과 일반 사용자에게 선택적으로 배포하는
경계를 설명합니다. 실제 대상 user 확인·file 설치 절차는
[Kubernetes admin kubeconfig runbook](../../operations/510-kubernetes-admin-kubeconfig-runbook.md)이
소유합니다.

## Credential 의미

`/etc/kubernetes/admin.conf`는 일반 application credential이 아니라 cluster-admin
credential입니다. Bootstrap `400`·`500`과 후속 root script는 이 root-owned file을 직접
사용하므로 일반 user home 복사는 cluster 구축의 필수 단계가 아닙니다.

`/etc/kubernetes/super-admin.conf`는 더 강한 break-glass 성격이며 일반 사용자에게 배포하지
않습니다.

## 선택적 배포 원칙

- 운영자가 실제 target user와 그 user에게 cluster-admin이 필요한 이유를 명시적으로
  확정합니다.
- `$SUDO_USER`·현재 shell user를 convenience로 추측하지 않습니다.
- OS account database에서 target home·primary group을 조회합니다.
- 기존 `${target_home}/.kube/config`가 있으면 다른 cluster context·credential owner를
  확인하기 전 덮어쓰지 않습니다.
- `.kube` directory는 target user만 접근하는 `0700`, config는 `0600`과 target user·group
  ownership을 사용합니다.
- 원본 admin.conf와 다른 user·super-admin credential은 변경하지 않습니다.

이 선택은 사람이 `kubectl`을 편하게 쓰기 위한 것보다 cluster-admin 권한을 local account에
복제하는 보안 효과가 더 큽니다. 필요가 없다면 배포하지 않는 것이 현재 기본입니다.

## 실패·회수 경계

대상 user·home·group 조회 실패를 다른 user 추측으로 우회하지 않습니다. Existing config는
자동 merge하지 않고 필요한 context·credential 병합 방식을 별도 결정합니다. 잘못된
user에게 배포한 사실을 알게 돼도 broad delete부터 실행하지 않고 target account, file
ownership·사용 여부와 credential 회수·교체 영향을 확인합니다.

현재 절차는 admin certificate rotation·RBAC 최소 권한 kubeconfig 발급·off-host access를
소유하지 않습니다. 여러 운영자나 장기 access가 필요하면 cluster-admin 복제 대신 역할별
RBAC·identity·certificate lifecycle을 별도로 설계합니다.

운영자 identity·credential lifecycle 후보의 상태와 도입 순서는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 관련 문서

- **실제 선택 배포 절차**: [Admin kubeconfig runbook](../../operations/510-kubernetes-admin-kubeconfig-runbook.md)
- **Admin credential 생성 state**: [150 Kubernetes cluster](150-kubernetes-cluster.md)
- **변경·회수 원칙**: [Infrastructure 변경과 복구](300-change-and-recovery-principles.md)

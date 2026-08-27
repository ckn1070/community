# Bootstrap script 목차

Bootstrap은 target의 최초 구축과 같은 목표의 재구축에 사용하는 변경만 소유합니다.
전체 구축 순서에서 먼저 실행된다는 뜻이 아니며, 이미 구축된 상태의 반복 운영 변경이나
점검은 각각 `operations`, `verify`에 둡니다.

| 기술 영역 | 결과 |
| --- | --- |
| [`kubernetes/`](kubernetes/README.md) | Ubuntu host를 단일 node Kubernetes cluster로 구축 |
| [`ecr/`](ecr/README.md) | kubelet의 private ECR credential provider 최초 구성 |
| [`gitops/`](gitops/README.md) | Argo CD Core 설치와 private GitOps repository 최초 연결 |
| [`application/`](application/README.md) | Application DB 접근 역할과 Git 밖의 Kubernetes 입력 resource 최초 구성 |
| [`load-balancer/`](load-balancer/README.md) | AWS Load Balancer Controller와 instance target용 node identity 최초 구성 |

실행 파일은 EC2 전달 archive에 함께 포함할 수 있지만, 각 README와 운영 runbook의 선행
조건을 충족한 시점에만 실행합니다. 전달 묶음은 실행 순서를 바꾸지 않습니다.

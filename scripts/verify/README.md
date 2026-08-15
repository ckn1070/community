# Verify script 목차

이 디렉터리는 구축 결과나 운영 상태를 반복 확인하는 점검을 소유합니다. 점검을 위해
수명이 짧은 resource를 만들 수 있지만, 성공 뒤 삭제하며 영구 목표 상태를 소유하지
않습니다.

| 기술 영역 | 점검 |
| --- | --- |
| [`ecr/`](ecr/README.md) | Private ECR exact digest의 Kubernetes pull·실행 확인 |

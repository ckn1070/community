# 문서 시스템과 실제 문서군 전면 이관 설계

이 문서는 repository 문서군을 현재 source에 맞게 이관할 때 필요한 장기 결정만 보존합니다.
현재 상태는 [작업 본문](005-documentation-system-and-corpus-migration.md)이 소유합니다.

## 결정 1. 독자 질문에서 owner를 선택합니다

현재 사실, 결정 근거, 작업 상태와 실행 절차를 서로 다른 문서 책임으로 분리합니다.
같은 사실을 여러 해상도의 문서에 복제하지 않고 router는 canonical owner로 가는 방향만
제공합니다.

## 결정 2. source와 문서를 양방향으로 대조합니다

source에서 독자에게 필요한 현재 사실이 빠지지 않았는지, 문서의 각 주장과 예시가 actual
source 또는 독자 행동에 필요한지 두 방향으로 확인합니다. 구조 검사나 link 존재만으로
의미 일치를 대신하지 않습니다.

## 결정 3. 작업 inventory는 영구 catalog가 아닙니다

대화 원문, dirty baseline, 반복 명령·commit과 Git으로 복구 가능한 변경 목록은 저장하지
않습니다. 다음 작업자가 재사용해야 할 현재 사실·결정·절차만 책임 문서에 승격합니다.

## 결정 4. 중대한 topology는 작성 전후에 독립 감사합니다

다문서 삭제·이동, canonical owner·번호·router 변경은 작성 전 proposal과 동결된 final
후보를 fresh reviewer가 actual source에서 독립 복원해 감사합니다. routine 문서 수정은
작성자의 두 번째 source-first 대조로 닫습니다.

## 결정 5. 삭제와 번호 이동을 같은 변경에서 닫습니다

artifact를 삭제하거나 이동하면 old path의 모든 inbound link, 대체 owner, group router와
현재 번호를 같은 변경에서 갱신합니다. 별도 tombstone이나 삭제 history 문서를 만들지
않고 Git history를 복구 owner로 사용합니다.

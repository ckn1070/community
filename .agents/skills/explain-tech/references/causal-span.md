# 선택 span의 causal handoff

사용자가 선택한 코드·설정에 `정확히 무엇인가`, `왜 필요한가`, `앞에서 했는데 왜 또
하는가`를 물었고 다음 중 하나가 성립하면 이 내부 evidence admission을 적용한다.

- argument·file·version·ID가 앞 단계의 출력이다.
- 같은 상위 목적에서 앞 단계와 선택 단계가 서로 다른 persistent state를 바꾼다.
- 선택 단계의 이유가 다음 restart·publish·validation·reconcile에서 드러난다.

## Source

caller를 따라 `persistent state를 실제로 생성·갱신한 exact 함수·명령 → 선택 span → 최초
consumer·관찰`을 확인한다. 가장 가까운 변수 대입이 새 artifact·version·ID를 남기면 그
값을 만든 persistent state producer까지 더 올라간다. 선택 span 밖의 line은 context일 뿐
새 line-by-line 대상이 아니다.

## 내부 evidence card

답변을 쓰기 전에 아래 값을 source에서 닫는다. 이 card는 조사 누락을 찾기 위한 내부
표현이며, field 이름·순서·heading을 사용자 답변에 복사하지 않는다.

```text
상위 이해 목표와 exact 질문
| producer의 exact source·직접 대상·before→after
| 선택 span이 받는 concrete 값과 쉬운 뜻
| 선택 span의 직접 대상·before→after
| 두 단계가 별도인 이유
| 최초 consumer·관찰 결과
| 결론에 필요한 선수 개념 중 사용자가 이해를 보인 것과 아직 닫히지 않은 것
```

값을 모두 확인한 뒤에도 사용자 답변에는 질문의 결론에 필요한 연결만 선택한다. 먼저 선택
span 전체를 미정의 기술 명사 없이 한 문장으로 설명하고 concrete 값이나 상태 변화 하나를
붙인다. producer와 consumer는 그 문장만으로 `왜 별도인가`가 닫히지 않을 때만 자연스러운
인과 순서로 펼친다. 실제로 비교할 단계가 여러 개일 때만 목록이나 작은 표를 사용한다.

## 실패 조건

- `앞 단계`, `현재 X`, `새 version`처럼 exact source·artifact·값을 숨긴다.
- 고유 이름을 일반 artifact 종류·생성 주체·담는 값 없이 처음 사용한다.
- producer와 선택 span이 서로 다른 object를 바꾸는데 한 상태 변화로 합친다.
- 1단계가 끝났는데 3단계가 필요한 이유를 단순히 `재시작하기 위해`처럼 결과 이름으로
  반복한다.
- 내부 card를 `만드는 단계`, `전달값`, `선택한 단계` 같은 고정 목차로 노출해 사용자가
  field 사이의 의미를 다시 조립하게 한다.
- 선택 span의 쉬운 전체 뜻보다 `artifact`, `object`, `producer`, `consumer` 같은 역할명을
  먼저 사용하거나, 그 역할명을 다른 미정의 기술 명사로 설명한다.
- 사용자가 묻지 않은 source field를 모두 출력해 국소 질문의 직접 답을 뒤로 민다.

하나라도 해당하면 첫 화면과 그에 의존한 상세를 다시 작성한다. 사용자가 직접 의미를
사용해 known임을 보인 값만 inline 정의를 생략한다. source token을 질문에 썼다는 사실만은
known 증거가 아니다.

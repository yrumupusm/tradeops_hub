# Research Area Selection

[한국어](../ko/research-area-selection.md) | [English](../en/research-area-selection.md)

The question screen allows users to select multiple areas to prioritize.

- `STRATEGIC_GOODS`: prioritize laws related to strategic goods.
- `DEFENSE_MATERIALS`: prioritize laws related to defense materials and defense science and technology.
- No selection: retrieve relevant laws together based on the question, as before.

These selections do not determine the legal classification of goods or technology, or whether permission is granted. They only prioritize search terms and laws for the selected areas; they do not exclude evidence from other areas. This preserves relevant evidence when both regulatory regimes may apply.

`POST /api/ask` and `POST /api/v1/ask` accept an optional `researchAreas` array. Snake_case-compatible requests also accept `research_areas`.

```json
{
  "question": "탱크를 수출하려고 하는데 관련 법령을 알려줘",
  "researchAreas": ["DEFENSE_MATERIALS"]
}
```

When defense materials/defense science and technology is selected, the answer footer directs the user to check DAPA export authorization or preliminary approval criteria. When strategic goods is selected, it directs the user to check strategic goods classification and export authorization criteria provided by 무역안보관리원.

For questions requesting a list of laws, such as `관련 법령이 뭐 있어?`, add the law names associated with the selected areas to the search terms and prioritize diversity across laws. The opening `검색된 관련 법령` list contains only laws with actually cited articles. Do not add law names without retrieved evidence.

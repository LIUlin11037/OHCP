# Mock examples only

Every file in this directory is fabricated UI demonstration data. These files are **not** an agreed backend contract, agronomic recommendation, knowledge source, or evidence of real API behavior. Do not use them for field decisions.

The endpoint DTOs and error envelopes remain provisional until the backend team supplies the OpenAPI contract and accepted response examples. These fixtures are consumed by `src/api/mock.ts` and intentionally show missing fields as absent or `未提供`. In the Mock mode, include `无匹配` or `降级` in the symptom description to display those two response states; a description shorter than 22 characters without an observed leaf age returns the follow-up example.

When the backend contract is frozen, add real shared examples in a separate directory and replace the Mock adapter. Keep all credentials out of examples.

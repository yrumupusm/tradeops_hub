import { Suspense } from "react";
import Console from "../components/console";
export default function Page() {
  return (
    <Suspense fallback={<p>업무 화면을 불러오는 중입니다.</p>}>
      <Console />
    </Suspense>
  );
}

// 백엔드 ErrorResponse{status, error, message, timestamp}에서 error는 에러 코드(예: GROUP_BUY_FULL),
// message가 사용자에게 보여줄 한글 메시지다.
export function getErrorMessage(err, fallback = '요청 처리 중 오류가 발생했습니다.') {
  return err?.response?.data?.message ?? fallback
}

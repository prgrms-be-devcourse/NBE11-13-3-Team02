import { useRef } from 'react'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'

let postcodeScriptPromise

function loadPostcodeScript() {
  if (window.kakao?.Postcode) return Promise.resolve()
  if (postcodeScriptPromise) return postcodeScriptPromise

  postcodeScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'
    script.onload = resolve
    script.onerror = () => reject(new Error('카카오 우편번호 서비스를 불러오지 못했습니다.'))
    document.head.appendChild(script)
  })
  return postcodeScriptPromise
}

export default function KakaoAddressFields({ form, setForm, onError }) {
  const detailRef = useRef(null)

  const searchAddress = async () => {
    try {
      await loadPostcodeScript()
      new window.kakao.Postcode({
        oncomplete: (data) => {
          const address = data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress
          setForm((previous) => ({ ...previous, zipCode: data.zonecode, address }))
          setTimeout(() => detailRef.current?.focus(), 0)
        },
      }).open()
    } catch (error) {
      onError?.(error.message)
    }
  }

  return (
    <>
      <Stack direction="row" spacing={1}>
        <TextField label="우편번호" value={form.zipCode} required fullWidth InputProps={{ readOnly: true }} />
        <Button variant="outlined" onClick={searchAddress} sx={{ whiteSpace: 'nowrap' }}>
          우편번호 찾기
        </Button>
      </Stack>
      <TextField label="주소" value={form.address} required fullWidth InputProps={{ readOnly: true }} />
      <TextField
        inputRef={detailRef}
        label="상세 주소"
        value={form.addressDetail}
        onChange={(event) => setForm((previous) => ({ ...previous, addressDetail: event.target.value }))}
        required
        fullWidth
      />
    </>
  )
}

// AI_GENERATE_START----------------
import { Alert, Button, Form, Input } from 'antd'
import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'

function MarketBackground() {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const context = canvas?.getContext('2d')
    if (!canvas || !context) return

    const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
    let reduceMotion = motionPreference.matches
    let animationFrame = 0
    let width = 0
    let height = 0

    const draw = (time: number) => {
      context.clearRect(0, 0, width, height)
      context.fillStyle = '#eef1f3'
      context.fillRect(0, 0, width, height)

      const gridSize = width <= 480 ? 38 : 58
      const gridOffset = reduceMotion ? 0 : (time * 0.01) % gridSize
      context.strokeStyle = 'rgba(20, 33, 31, 0.07)'
      context.lineWidth = 1
      for (let x = -gridOffset; x <= width + gridSize; x += gridSize) {
        context.beginPath()
        context.moveTo(x, 0)
        context.lineTo(x, height)
        context.stroke()
      }
      for (let y = 0; y <= height; y += gridSize) {
        context.beginPath()
        context.moveTo(0, y)
        context.lineTo(width, y)
        context.stroke()
      }

      const lineSpacing = width <= 480 ? 24 : 34
      const lineShift = reduceMotion ? 0 : time * 0.016
      context.beginPath()
      for (let x = -lineSpacing; x <= width + lineSpacing; x += lineSpacing) {
        const phase = (x + lineShift) / 118
        const y = height * 0.58
          + Math.sin(phase) * height * 0.075
          + Math.cos(phase * 0.52) * height * 0.038
        if (x === -lineSpacing) context.moveTo(x, y)
        else context.lineTo(x, y)
      }
      context.strokeStyle = 'rgba(22, 123, 104, 0.42)'
      context.lineWidth = 2
      context.stroke()

      const candleSpacing = width <= 480 ? 48 : 68
      const candleWidth = width <= 480 ? 7 : 9
      const candleOffset = reduceMotion ? 0 : (time * 0.014) % candleSpacing
      const firstIndex = Math.floor((reduceMotion ? 0 : time * 0.014) / candleSpacing)
      for (let x = -candleSpacing - candleOffset, index = firstIndex - 1;
        x <= width + candleSpacing;
        x += candleSpacing, index += 1) {
        const center = height * 0.36
          + Math.sin(index * 1.31) * height * 0.1
          + Math.cos(index * 0.57) * height * 0.035
        const movement = Math.sin(index * 2.17) * height * 0.03
        const open = center - movement
        const close = center + movement
        const high = Math.min(open, close) - height * 0.025
        const low = Math.max(open, close) + height * 0.025
        const rising = close <= open
        const color = rising ? 'rgba(180, 35, 24, 0.38)' : 'rgba(22, 123, 104, 0.38)'

        context.strokeStyle = color
        context.fillStyle = color
        context.beginPath()
        context.moveTo(x, high)
        context.lineTo(x, low)
        context.stroke()
        context.fillRect(x - candleWidth / 2, Math.min(open, close), candleWidth, Math.max(3, Math.abs(close - open)))

        const volumeHeight = height * (0.03 + Math.abs(Math.sin(index * 0.83)) * 0.07)
        context.fillStyle = rising ? 'rgba(180, 35, 24, 0.1)' : 'rgba(22, 123, 104, 0.1)'
        context.fillRect(x - candleWidth, height - volumeHeight - 20, candleWidth * 2, volumeHeight)
      }

      if (!reduceMotion) animationFrame = window.requestAnimationFrame(draw)
    }

    const resize = () => {
      const bounds = canvas.getBoundingClientRect()
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
      width = bounds.width
      height = bounds.height
      canvas.width = Math.round(width * pixelRatio)
      canvas.height = Math.round(height * pixelRatio)
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
      if (reduceMotion) draw(0)
    }

    const handleMotionPreference = (event: MediaQueryListEvent) => {
      reduceMotion = event.matches
      window.cancelAnimationFrame(animationFrame)
      draw(0)
    }

    const resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(canvas)
    motionPreference.addEventListener('change', handleMotionPreference)
    resize()
    draw(0)

    return () => {
      window.cancelAnimationFrame(animationFrame)
      resizeObserver.disconnect()
      motionPreference.removeEventListener('change', handleMotionPreference)
    }
  }, [])

  return <canvas ref={canvasRef} className="login-market-canvas" aria-hidden="true" />
}

export default function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  if (auth.username) return <Navigate to="/" replace />

  const submit = async (values: { username: string; password: string }) => {
    setSubmitting(true)
    setError(null)
    try {
      await auth.login(values.username, values.password)
      const target = (location.state as { from?: string } | null)?.from || '/'
      navigate(target, { replace: true })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return <main className="login-page">
    <MarketBackground />
    <div className="login-background-overlay" aria-hidden="true" />
    <header className="login-brand">
      <img src="/system-mark.svg" alt="" />
      <div><strong>股票交易控制台</strong><span>STOCK CONSOLE</span></div>
    </header>
    <section className="login-form" aria-labelledby="login-title">
      <div className="login-form-heading">
        <img src="/system-mark.svg" alt="" />
        <h1 id="login-title">登录控制台</h1>
        <p>账户、交易、模型与运行事实的只读工作台</p>
      </div>
      {error && <Alert type="error" showIcon message={error} />}
      <Form layout="vertical" onFinish={submit} requiredMark={false} size="large">
        <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input prefix={<UserOutlined />} autoComplete="username" />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={submitting} block>登录</Button>
      </Form>
      <p className="login-form-footnote">只读访问 Python 交易事实</p>
    </section>
  </main>
}
// AI_GENERATE_END----------------

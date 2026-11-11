const params = new URLSearchParams(location.search);
const box = document.getElementById('login-message');
if (params.has('error')) {
  box.textContent = '用户名或密码不正确，请重试。';
  box.classList.add('visible', 'error');
} else if (params.has('logout')) {
  box.textContent = '已安全退出。';
  box.classList.add('visible');
}

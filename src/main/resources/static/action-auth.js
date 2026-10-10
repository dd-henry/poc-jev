async function fetchWithActionAuthentication(url, options) {
    let response = await fetch(url, options);
    if (response.status !== 401) return response;

    const password = window.prompt('Informe a senha para executar esta ação:');
    if (password === null) throw new Error('Ação cancelada.');

    const loginResponse = await fetch('/login', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({username: 'friends', password})
    });
    if (loginResponse.url.includes('/login?error')) {
        throw new Error('Senha incorreta. Tente novamente.');
    }

    response = await fetch(url, options);
    if (response.status === 401) {
        throw new Error('Não foi possível autenticar. Tente novamente.');
    }
    return response;
}

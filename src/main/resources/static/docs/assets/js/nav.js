const pages = {
    rest: './restapi/index.html',
    async: './asyncapi/index.html',
};

const viewer = document.getElementById('viewer');
const buttons = {
    rest: document.getElementById('btn-rest'),
    async: document.getElementById('btn-async'),
};

function showTab(name) {
    if (viewer.src.endsWith(pages[name])) return;
    viewer.src = pages[name];
    Object.entries(buttons).forEach(([key, btn]) => {
        btn.classList.toggle('active', key === name);
    });
}

buttons.rest.addEventListener('click', () => showTab('rest'));
buttons.async.addEventListener('click', () => showTab('async'));

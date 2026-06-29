(() => {
    'use strict';

    const root = document.documentElement;
    const storageKeys = {
        theme: 'jwt-blog.theme',
        font: 'jwt-blog.readerFont',
        scale: 'jwt-blog.readerScale'
    };
    const allowedFonts = new Set(['sans', 'serif', 'mono']);
    const allowedScales = new Set(['small', 'medium', 'large']);
    const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
    let storageAvailable = true;

    const read = key => {
        if (!storageAvailable) return null;
        try { return localStorage.getItem(key); }
        catch (error) { storageAvailable = false; return null; }
    };
    const write = (key, value) => {
        if (!storageAvailable) return;
        try { localStorage.setItem(key, value); }
        catch (error) { storageAvailable = false; }
    };
    const preferredTheme = () => {
        const stored = read(storageKeys.theme);
        if (!storageAvailable) return 'light';
        if (stored === 'light' || stored === 'dark') return stored;
        return media && media.matches ? 'dark' : 'light';
    };

    function updateThemeControls() {
        const dark = root.dataset.bsTheme === 'dark';
        document.querySelectorAll('[data-theme-toggle]').forEach(button => {
            button.setAttribute('aria-label', dark ? '라이트 모드로 전환' : '다크 모드로 전환');
            button.setAttribute('aria-pressed', String(dark));
            button.title = dark ? '라이트 모드' : '다크 모드';
        });
    }

    function setTheme(theme, persist) {
        const resolved = theme === 'dark' ? 'dark' : 'light';
        root.dataset.bsTheme = resolved;
        if (persist) write(storageKeys.theme, resolved);
        updateThemeControls();
        window.dispatchEvent(new CustomEvent('jwtblog:themechange', {detail: {theme: resolved}}));
    }

    function setReaderPreference(type, value, persist) {
        if (type === 'font' && allowedFonts.has(value)) {
            root.dataset.readerFont = value;
            if (persist) write(storageKeys.font, value);
        }
        if (type === 'scale' && allowedScales.has(value)) {
            root.dataset.readerScale = value;
            if (persist) write(storageKeys.scale, value);
        }
        syncReaderControls();
    }

    function syncReaderControls() {
        document.querySelectorAll('[data-reader-font]').forEach(input => {
            input.checked = input.value === root.dataset.readerFont;
        });
        document.querySelectorAll('[data-reader-scale]').forEach(input => {
            input.checked = input.value === root.dataset.readerScale;
        });
    }

    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
        button.addEventListener('click', () => setTheme(root.dataset.bsTheme === 'dark' ? 'light' : 'dark', true));
    });
    document.querySelectorAll('[data-reader-font]').forEach(input => {
        input.addEventListener('change', () => setReaderPreference('font', input.value, true));
    });
    document.querySelectorAll('[data-reader-scale]').forEach(input => {
        input.addEventListener('change', () => setReaderPreference('scale', input.value, true));
    });

    if (!allowedFonts.has(root.dataset.readerFont)) root.dataset.readerFont = 'sans';
    if (!allowedScales.has(root.dataset.readerScale)) root.dataset.readerScale = 'medium';
    setTheme(preferredTheme(), false);
    syncReaderControls();

    if (media) {
        media.addEventListener('change', event => {
            if (storageAvailable && !read(storageKeys.theme)) setTheme(event.matches ? 'dark' : 'light', false);
        });
    }

    const confirmElement = document.getElementById('confirmActionModal');
    if (confirmElement && window.bootstrap) {
        const confirmModal = bootstrap.Modal.getOrCreateInstance(confirmElement);
        const title = document.getElementById('confirmActionTitle');
        const message = document.getElementById('confirmActionMessage');
        const submitButton = confirmElement.querySelector('[data-confirm-submit]');
        let pendingForm = null;
        let pendingSubmitter = null;

        document.querySelectorAll('form').forEach(form => {
            form.addEventListener('submit', event => {
                if (form.dataset.confirmed === 'true') return;
                const submitter = event.submitter;
                const source = submitter && submitter.hasAttribute('data-confirm') ? submitter : form;
                if (!source.hasAttribute('data-confirm')) return;
                event.preventDefault();
                pendingForm = form;
                pendingSubmitter = submitter;
                title.textContent = source.dataset.confirmTitle || '작업 확인';
                message.textContent = source.dataset.confirm || '이 작업을 진행하시겠습니까?';
                submitButton.textContent = source.dataset.confirmButton || '진행';
                confirmModal.show();
            });
        });

        submitButton.addEventListener('click', () => {
            if (!pendingForm) return;
            pendingForm.dataset.confirmed = 'true';
            pendingForm.requestSubmit(pendingSubmitter || undefined);
            pendingForm = null;
            pendingSubmitter = null;
            confirmModal.hide();
        });
        confirmElement.addEventListener('hidden.bs.modal', () => { pendingForm = null; pendingSubmitter = null; });
    }
})();

async page => {
  const results = {
    steps: [],
    toolbarSpecDownload: null,
    toolbarBackendDownload: null,
    milestoneBackendDownload: null,
    milestoneSpecDownload: null,
    topbarText: '',
    success: false
  };

  try {
    results.steps.push('1. Navegando a la URL raíz http://localhost:5173...');
    await page.goto('http://localhost:5173');
    await page.waitForTimeout(1000);

    // Iniciar sesión si estamos en la pantalla de login
    const loginButton = page.getByRole('button', { name: 'Entrar' });
    if (await loginButton.isVisible()) {
      results.steps.push('2. Iniciando sesión con demo@local.test en modo headed...');
      await page.locator('input[name="email"], input[type="email"]').fill('demo@local.test');
      await page.locator('input[name="password"], input[type="password"]').fill('ClaveSegura1');
      await loginButton.click();
      await page.waitForTimeout(2000);
    }

    results.steps.push('3. Accediendo al editor del diagrama "prueba"...');
    await page.goto('http://localhost:5173/?diagram=d2c4e086-0451-4de4-bfef-88af39d7cf28');
    await page.waitForTimeout(3000);

    // Verificar barra superior y canvas
    results.steps.push('4. Verificando barra de herramientas y lienzo...');
    await page.waitForSelector('.topbar', { timeout: 10000 });
    results.topbarText = await page.locator('.topbar').textContent();

    await page.screenshot({ path: './scratch/1_editor_canvas.png' });
    results.steps.push('5. Captura guardada: scratch/1_editor_canvas.png');

    // --- PRUEBA 1: Barra superior - Descargar Spec móvil ---
    results.steps.push('6. Barra superior: Pulsando "Spec móvil"...');
    const specPromise1 = page.waitForEvent('download', { timeout: 15000 });
    await page.getByRole('button', { name: 'Spec móvil' }).click();
    const specDownload1 = await specPromise1;
    const specFilename1 = specDownload1.suggestedFilename();
    await specDownload1.saveAs('./scratch/' + specFilename1);
    results.toolbarSpecDownload = specFilename1;
    results.steps.push('7. Spec móvil descargado desde barra superior: ' + specFilename1);

    // --- PRUEBA 2: Barra superior - Generar backend (ZIP) ---
    results.steps.push('8. Barra superior: Pulsando "Generar backend"...');
    const zipPromise1 = page.waitForEvent('download', { timeout: 25000 });
    await page.getByRole('button', { name: 'Generar backend' }).click();
    const zipDownload1 = await zipPromise1;
    const zipFilename1 = zipDownload1.suggestedFilename();
    await zipDownload1.saveAs('./scratch/' + zipFilename1);
    results.toolbarBackendDownload = zipFilename1;
    results.steps.push('9. Backend ZIP descargado desde barra superior: ' + zipFilename1);

    // --- PRUEBA 3: Panel de Hitos / Versiones ---
    results.steps.push('10. Abriendo pestaña "Hitos" en el panel lateral...');
    await page.getByRole('button', { name: 'Hitos' }).click();
    await page.waitForTimeout(1000);

    // Guardar un nuevo hito para la prueba
    const hitoName = 'Hito Playwright ' + Date.now();
    results.steps.push('11. Creando nuevo hito "' + hitoName + '"...');
    const labelInput = page.locator('input[name="label"]');
    if (await labelInput.isVisible()) {
      await labelInput.fill(hitoName);
      await page.getByRole('button', { name: 'Guardar hito' }).click();
      await page.waitForTimeout(2000);
    }

    await page.screenshot({ path: './scratch/2_hitos_panel.png' });
    results.steps.push('12. Captura guardada: scratch/2_hitos_panel.png');

    // Hacer clic en el hito recién creado o el primer hito disponible
    results.steps.push('13. Abriendo modal de vista previa del hito...');
    const hitoButton = page.locator('.version-list button').first();
    await hitoButton.click();
    await page.waitForTimeout(1000);

    await page.screenshot({ path: './scratch/3_modal_hito_preview.png' });
    results.steps.push('14. Captura guardada: scratch/3_modal_hito_preview.png');

    // Descargar backend (ZIP) desde el modal del hito
    results.steps.push('15. Modal del hito: Pulsando "Descargar backend (ZIP)"...');
    const zipPromise2 = page.waitForEvent('download', { timeout: 25000 });
    await page.getByRole('button', { name: 'Descargar backend (ZIP)' }).click();
    const zipDownload2 = await zipPromise2;
    const zipFilename2 = 'milestone-' + zipDownload2.suggestedFilename();
    await zipDownload2.saveAs('./scratch/' + zipFilename2);
    results.milestoneBackendDownload = zipFilename2;
    results.steps.push('16. Backend ZIP descargado desde modal del hito: ' + zipFilename2);

    // Descargar spec móvil desde el modal del hito
    results.steps.push('17. Modal del hito: Pulsando "Descargar spec móvil"...');
    const specPromise2 = page.waitForEvent('download', { timeout: 15000 });
    await page.getByRole('button', { name: 'Descargar spec móvil' }).click();
    const specDownload2 = await specPromise2;
    const specFilename2 = 'milestone-' + specDownload2.suggestedFilename();
    await specDownload2.saveAs('./scratch/' + specFilename2);
    results.milestoneSpecDownload = specFilename2;
    results.steps.push('18. Spec móvil descargado desde modal del hito: ' + specFilename2);

    // Cerrar modal
    await page.getByRole('button', { name: 'Cerrar' }).click();
    await page.waitForTimeout(1000);

    await page.screenshot({ path: './scratch/4_complete.png' });
    results.steps.push('19. Flujo completo finalizado exitosamente.');

    results.success = true;
    return results;
  } catch (err) {
    results.error = err.message || String(err);
    return results;
  }
}

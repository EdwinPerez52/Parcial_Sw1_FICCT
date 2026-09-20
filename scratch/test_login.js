async page => {
  console.log('Filling email and password...');
  await page.locator('input[name="email"], input[type="email"]').fill('demo@local.test');
  await page.locator('input[name="password"], input[type="password"]').fill('ClaveSegura1');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.waitForTimeout(2000);
  const url = page.url();
  const text = await page.textContent('body');
  return { url, title: await page.title(), hasError: text.includes('inválid') || text.includes('error') };
}

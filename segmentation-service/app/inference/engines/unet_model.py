from __future__ import annotations

import torch
from torch import Tensor, nn


class DoubleConv(nn.Module):
    """Two consecutive convolution blocks used throughout the U-Net."""

    def __init__(self, in_channels: int, out_channels: int):
        super().__init__()
        self.block = nn.Sequential(
            nn.Conv2d(in_channels, out_channels, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(out_channels),
            nn.ReLU(inplace=True),
            nn.Conv2d(out_channels, out_channels, kernel_size=3, padding=1, bias=False),
            nn.BatchNorm2d(out_channels),
            nn.ReLU(inplace=True),
        )

    def forward(self, value: Tensor) -> Tensor:
        return self.block(value)


class DownBlock(nn.Module):
    """Encoder step: max-pool first, then refine with DoubleConv."""

    def __init__(self, in_channels: int, out_channels: int):
        super().__init__()
        self.block = nn.Sequential(
            nn.MaxPool2d(kernel_size=2, stride=2),
            DoubleConv(in_channels, out_channels),
        )

    def forward(self, value: Tensor) -> Tensor:
        return self.block(value)


class UpBlock(nn.Module):
    """Decoder step: upsample, concatenate with skip features, then refine."""

    def __init__(self, in_channels: int, skip_channels: int, out_channels: int):
        super().__init__()
        self.upsample = nn.ConvTranspose2d(in_channels, in_channels // 2, kernel_size=2, stride=2)
        self.conv = DoubleConv((in_channels // 2) + skip_channels, out_channels)

    def forward(self, value: Tensor, skip: Tensor) -> Tensor:
        value = self.upsample(value)

        # Shape alignment is needed because odd-sized images can drift by one pixel.
        diff_y = skip.size(2) - value.size(2)
        diff_x = skip.size(3) - value.size(3)
        value = nn.functional.pad(
            value,
            [diff_x // 2, diff_x - diff_x // 2, diff_y // 2, diff_y - diff_y // 2],
        )

        value = torch.cat([skip, value], dim=1)
        return self.conv(value)


class UNet2D(nn.Module):
    """Standard 2D U-Net for dense segmentation."""

    def __init__(self, in_channels: int, out_channels: int, base_channels: int = 32):
        super().__init__()
        self.input_block = DoubleConv(in_channels, base_channels)
        self.down1 = DownBlock(base_channels, base_channels * 2)
        self.down2 = DownBlock(base_channels * 2, base_channels * 4)
        self.down3 = DownBlock(base_channels * 4, base_channels * 8)
        self.bottom = DownBlock(base_channels * 8, base_channels * 16)
        self.up1 = UpBlock(base_channels * 16, base_channels * 8, base_channels * 8)
        self.up2 = UpBlock(base_channels * 8, base_channels * 4, base_channels * 4)
        self.up3 = UpBlock(base_channels * 4, base_channels * 2, base_channels * 2)
        self.up4 = UpBlock(base_channels * 2, base_channels, base_channels)
        self.output = nn.Conv2d(base_channels, out_channels, kernel_size=1)

    def forward(self, value: Tensor) -> Tensor:
        x1 = self.input_block(value)
        x2 = self.down1(x1)
        x3 = self.down2(x2)
        x4 = self.down3(x3)
        x5 = self.bottom(x4)
        x = self.up1(x5, x4)
        x = self.up2(x, x3)
        x = self.up3(x, x2)
        x = self.up4(x, x1)
        return self.output(x)

